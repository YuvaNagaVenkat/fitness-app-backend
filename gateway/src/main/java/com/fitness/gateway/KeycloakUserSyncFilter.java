package com.fitness.gateway;

import com.fitness.gateway.User.RegisterRequest;
import com.fitness.gateway.User.UserService;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KeycloakUserSyncFilter implements WebFilter {
    private final UserService userService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain){
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-ID");
//        System.out.println("Token:"+token);
        RegisterRequest registerRequest = getUserDetails(token);
        System.out.println("Hii I am Here 1");
        if(userId == null){
            System.out.println("Hii I am Here 2");
            userId = registerRequest.getKeycloakId();
            System.out.println(userId);
        }
        if(userId != null && token != null) {
            System.out.println("Hii I am Here");
            String finalUserId = userId;
            return userService.validateUser(userId)
                    .flatMap(exist -> {
                        if (!exist) {
                            //Register
                            if(registerRequest != null){
                                return userService.registerUser(registerRequest)
                                        .then(Mono.empty());
                            }
                            else{
                                return Mono.empty();
                            }
                        } else {
                            System.out.println("User Already Exists Skipping Sync.");
                            return Mono.empty();
                        }
                    })
                    .then(Mono.defer(() -> {
                        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                .header("X-User-ID", finalUserId).build();
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    }));
        }
        return chain.filter(exchange);
    }
    public RegisterRequest getUserDetails(String token){

        try{
            String tokenWithOutBearer = token.replace("Bearer","").trim();
            SignedJWT signedJWT = SignedJWT.parse(tokenWithOutBearer);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            System.out.println(claims.getStringClaim("sub"));
            RegisterRequest registerRequest = new RegisterRequest();
            registerRequest.setEmail(claims.getStringClaim("email"));
            registerRequest.setKeycloakId(claims.getStringClaim("sub"));

            registerRequest.setPassword("dummy@123123");
            registerRequest.setFirstName(claims.getStringClaim("given_name"));
            registerRequest.setLastName(claims.getStringClaim("family_name"));
            return registerRequest;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}

package com.server.contestControl.authServer.service.jwt.strategy;

import com.server.contestControl.authServer.service.jwt.core.JwtKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class AccessTokenStrategy implements TokenStrategy {

    private final JwtKeyProvider keyProvider;

    @Value("${jwt.expiration}")
    private long expiration;

    @Override
    public String generateToken(UserDetails userDetails, Long id) {

        // Extract roles / authorities
        var authorities = userDetails.getAuthorities()
                .stream()
                .map(auth -> auth.getAuthority()) // e.g. ROLE_ADMIN
                .toList();

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .claim("type", "access")

                // 🔑 ADD THIS
                .claim("authorities", authorities)

                .signWith(keyProvider.getAccessKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    @Override
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(keyProvider.getAccessKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Override
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        String username = claims.getSubject();
        Date expirationDate = claims.getExpiration();
        return username.equals(userDetails.getUsername()) &&
                expirationDate.after(new Date());
    }

    @Override
    public String getTokenType() {
        return "access";
    }

    @Override
    public long getTokenExpiration() {
        return  expiration;
    }
}
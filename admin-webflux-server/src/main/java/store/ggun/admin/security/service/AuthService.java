package store.ggun.admin.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import store.ggun.admin.admin.AdminModel;
import store.ggun.admin.admin.AdminRepository;
import store.ggun.admin.security.util.JwtUtil;


import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenService tokenService;
    private final JwtUtil jwtUtil;
    private final AdminRepository adminRepository;

    public Mono<Map<String, String>> login(AdminModel adminModel) {
        return adminRepository.findByUsername(adminModel.getUsername())
                .flatMap(existingAdmin -> {
                    if (existingAdmin.getPassword().equals(adminModel.getPassword())) {
                        String accessToken = jwtUtil.generateAccessToken(existingAdmin);
                        String refreshToken = jwtUtil.generateRefreshToken(existingAdmin);
                        return tokenService.saveRefreshToken(existingAdmin.getId(), refreshToken, jwtUtil.getRefreshExpiration())
                                .then(Mono.just(Map.of("accessToken", accessToken, "refreshToken", refreshToken)));
                    } else {
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }
                });
    }

    public Mono<Map<String, String>> refreshTokens(String refreshToken) {
        String username = jwtUtil.extractUsername(refreshToken);
        return adminRepository.findByUsername(username)
                .flatMap(admin -> tokenService.getRefreshToken(admin.getId())
                        .flatMap(savedRefreshToken -> {
                            if (savedRefreshToken.equals(refreshToken) && jwtUtil.validateToken(refreshToken, admin)) {
                                String newAccessToken = jwtUtil.generateAccessToken(admin);
                                String newRefreshToken = jwtUtil.generateRefreshToken(admin);
                                return tokenService.saveRefreshToken(admin.getId(), newRefreshToken, jwtUtil.getRefreshExpiration())
                                        .then(Mono.just(Map.of("accessToken", newAccessToken, "refreshToken", newRefreshToken)));
                            } else {
                                return Mono.error(new RuntimeException("Invalid refresh token"));
                            }
                        }));
    }

    public Mono<Void> logout(String adminId) {
        return tokenService.deleteRefreshToken(adminId).then();
    }
}
package br.com.conectaPro.api.security;

import br.com.conectaPro.dto.ForgotPasswordDTO;
import br.com.conectaPro.dto.LoginRequestDTO;
import br.com.conectaPro.dto.LoginResponseDTO;
import br.com.conectaPro.dto.ResetPasswordDTO;
import br.com.conectaPro.model.user.User;
import br.com.conectaPro.model.user.UserRepository;
import br.com.conectaPro.security.CustomUserDetails;
import br.com.conectaPro.security.EmailService;
import br.com.conectaPro.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth/Login")
public class AuthController {

  private final UserRepository userRepository;
  private final JwtService jwtService;
  private final EmailService emailService;
  private final PasswordEncoder passwordEncoder;

  @Operation(summary = "Faz login no sistema")
  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequestDTO request) {
    Optional<User> userOpt = userRepository.findByEmail(request.email());
    if (userOpt.isPresent()) {
      User user = userOpt.get();
      // Verifica senha usando BCrypt
      if (passwordEncoder.matches(request.password(), user.getPassword())) {
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(
            new LoginResponseDTO(token, user.getId(), user.getName(), user.getUserType()));
      }
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("E-mail ou senha inválidos");
  }

  @Operation(summary = "Solicita recuperação de senha")
  @PostMapping("/forgot-password")
  public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordDTO request) {
    // A resposta é sempre a mesma, exista o e-mail ou não: assim ninguém consegue descobrir quais
    // e-mails estão cadastrados (enumeração de usuários).
    Optional<User> userOpt = userRepository.findByEmail(request.email());
    if (userOpt.isPresent()) {
      User user = userOpt.get();
      String token = UUID.randomUUID().toString();
      user.setRecoveryToken(token);
      user.setRecoveryTokenExpiration(LocalDateTime.now().plusMinutes(30));
      userRepository.save(user);
      emailService.sendRecoveryEmail(user.getEmail(), token);
    }

    return ResponseEntity.ok(
        "Se o e-mail estiver cadastrado, enviaremos um link para redefinir a senha.");
  }

  @Operation(summary = "Redefine a senha")
  @PostMapping("/reset-password")
  public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordDTO request) {
    Optional<User> userOpt = userRepository.findByRecoveryToken(request.token());
    if (userOpt.isEmpty()) {
      return ResponseEntity.badRequest().body("Token inválido.");
    }
    User user = userOpt.get();
    if (user.getRecoveryTokenExpiration() == null
        || user.getRecoveryTokenExpiration().isBefore(LocalDateTime.now())) {
      return ResponseEntity.badRequest().body("Token expirado.");
    }
    // Criptografa a nova senha antes de salvar
    user.setPassword(passwordEncoder.encode(request.newPassword()));
    user.setRecoveryToken(null);
    user.setRecoveryTokenExpiration(null);
    userRepository.save(user);

    // E-mail de segurança: avisa o dono da conta que a senha mudou.
    emailService.sendPasswordChangedEmail(user.getEmail(), user.getName());

    return ResponseEntity.ok("Senha alterada com sucesso.");
  }
}

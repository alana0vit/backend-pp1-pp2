package br.com.conectaPro.util;

import br.com.conectaPro.security.CustomUserDetails;
import java.util.NoSuchElementException;

/**
 * Checagens de "dono" usadas pelos controllers. Quando o usuário logado não é o dono, responde
 * "não encontrado" (404 via GlobalExceptionHandler) para não revelar que o recurso existe.
 */
public final class Authz {

  private Authz() {}

  public static Long userId(CustomUserDetails principal) {
    if (principal == null || principal.getUser() == null) {
      throw new NoSuchElementException("Recurso não encontrado.");
    }
    return principal.getUser().getId();
  }

  /** O usuário logado precisa ser exatamente {@code ownerId}. */
  public static void requireSelf(Long ownerId, CustomUserDetails principal) {
    Long uid = userId(principal);
    if (ownerId == null || !ownerId.equals(uid)) {
      throw new NoSuchElementException("Recurso não encontrado.");
    }
  }
}

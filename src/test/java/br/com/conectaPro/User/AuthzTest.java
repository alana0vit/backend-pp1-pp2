package br.com.conectaPro.User;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.conectaPro.model.user.User;
import br.com.conectaPro.security.CustomUserDetails;
import br.com.conectaPro.util.Authz;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthzTest {

  private CustomUserDetails logado(long id) {
    User u = new User();
    u.setId(id);
    return new CustomUserDetails(u);
  }

  @Test
  @DisplayName("Dono passa; outro usuário, id nulo ou sem login recebem 404")
  void requireSelf() {
    assertDoesNotThrow(() -> Authz.requireSelf(1L, logado(1L)));
    assertThrows(NoSuchElementException.class, () -> Authz.requireSelf(2L, logado(1L)));
    assertThrows(NoSuchElementException.class, () -> Authz.requireSelf(null, logado(1L)));
    assertThrows(NoSuchElementException.class, () -> Authz.requireSelf(1L, null));
  }
}

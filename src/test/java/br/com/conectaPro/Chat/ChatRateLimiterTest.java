package br.com.conectaPro.Chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.conectaPro.model.chat.ChatRateLimiter;
import br.com.conectaPro.model.chat.ChatRateLimiter.Decision;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRateLimiterTest {

  @Test
  @DisplayName("Bloqueia o cliente após o limite e libera quando a janela passa")
  void limitePorCliente() {
    AtomicLong agora = new AtomicLong(1_000_000);
    ChatRateLimiter limiter = new ChatRateLimiter(2, 60_000, 100, agora::get);

    assertEquals(Decision.ALLOWED, limiter.tryAcquire("1.1.1.1"));
    assertEquals(Decision.ALLOWED, limiter.tryAcquire("1.1.1.1"));
    assertEquals(Decision.CLIENT_LIMIT, limiter.tryAcquire("1.1.1.1"));
    assertEquals(Decision.ALLOWED, limiter.tryAcquire("2.2.2.2"));

    agora.addAndGet(60_001);
    assertEquals(Decision.ALLOWED, limiter.tryAcquire("1.1.1.1"));
  }

  @Test
  @DisplayName("Teto global diário protege o free tier e zera no dia seguinte")
  void limiteDiario() {
    AtomicLong agora = new AtomicLong(0);
    ChatRateLimiter limiter = new ChatRateLimiter(10, 60_000, 2, agora::get);

    assertEquals(Decision.ALLOWED, limiter.tryAcquire("a"));
    assertEquals(Decision.ALLOWED, limiter.tryAcquire("b"));
    assertEquals(Decision.DAILY_LIMIT, limiter.tryAcquire("c"));

    agora.addAndGet(24L * 60 * 60 * 1000);
    assertEquals(Decision.ALLOWED, limiter.tryAcquire("c"));
  }
}

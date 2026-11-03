package br.com.conectaPro.model.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limita o uso do chat público: um teto por cliente (IP) numa janela deslizante e um teto global
 * por dia, para não esgotar o free tier do Workers AI. Em memória (zera ao reiniciar a aplicação).
 */
@Component
public class ChatRateLimiter {

  public enum Decision {
    ALLOWED,
    CLIENT_LIMIT,
    DAILY_LIMIT
  }

  private static final long DIA_MS = 24L * 60 * 60 * 1000;
  private static final int LIMPEZA_ACIMA_DE = 5000;

  private final int maxPorJanela;
  private final long janelaMs;
  private final int maxPorDia;
  private final LongSupplier relogio;

  private final Map<String, Deque<Long>> porCliente = new HashMap<>();
  private long diaAtual = -1;
  private int usoNoDia = 0;

  @Autowired
  public ChatRateLimiter(
      @Value("${chat.rate-limit.per-client:20}") int maxPorJanela,
      @Value("${chat.rate-limit.window-minutes:60}") long janelaMinutos,
      @Value("${chat.rate-limit.per-day:2000}") int maxPorDia) {
    this(maxPorJanela, janelaMinutos * 60_000L, maxPorDia, System::currentTimeMillis);
  }

  public ChatRateLimiter(int maxPorJanela, long janelaMs, int maxPorDia, LongSupplier relogio) {
    this.maxPorJanela = maxPorJanela;
    this.janelaMs = janelaMs;
    this.maxPorDia = maxPorDia;
    this.relogio = relogio;
  }

  public synchronized Decision tryAcquire(String clientKey) {
    long agora = relogio.getAsLong();

    long dia = agora / DIA_MS;
    if (dia != diaAtual) {
      diaAtual = dia;
      usoNoDia = 0;
    }
    if (usoNoDia >= maxPorDia) {
      return Decision.DAILY_LIMIT;
    }

    if (porCliente.size() > LIMPEZA_ACIMA_DE) {
      porCliente.values().removeIf(d -> d.isEmpty() || d.peekLast() < agora - janelaMs);
    }

    Deque<Long> usos = porCliente.computeIfAbsent(clientKey, k -> new ArrayDeque<>());
    while (!usos.isEmpty() && usos.peekFirst() <= agora - janelaMs) {
      usos.pollFirst();
    }
    if (usos.size() >= maxPorJanela) {
      return Decision.CLIENT_LIMIT;
    }

    usos.addLast(agora);
    usoNoDia++;
    return Decision.ALLOWED;
  }
}

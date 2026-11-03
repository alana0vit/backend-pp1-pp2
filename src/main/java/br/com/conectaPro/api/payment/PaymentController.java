package br.com.conectaPro.api.payment;

import br.com.conectaPro.model.payment.Payment;
import br.com.conectaPro.model.payment.PaymentService;
import br.com.conectaPro.model.payment.PaymentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin
@Tag(
    name = "Payments",
    description =
        "Cobrança/pagamento das demandas. Hoje usa um gateway simulado (nenhum dinheiro real é"
            + " movimentado); a interface foi desenhada para trocar por um provedor real (ex:"
            + " Mercado Pago) sem mudar o resto do sistema.")
public class PaymentController {

  @Autowired private PaymentService paymentService;

  /** URL do site (front): para onde o checkout simulado devolve o usuário. */
  @Value("${app.frontend.url:http://localhost:5173}")
  private String frontendUrl;

  @Operation(summary = "Gera uma cobrança para a demanda (deve estar AGUARDANDO_PAGAMENTO)")
  @PostMapping("/demand/{demandId}/checkout")
  public ResponseEntity<?> criarCheckoutDemanda(@PathVariable Long demandId) {
    try {
      Payment payment = paymentService.criarCheckoutDemanda(demandId);
      String checkoutUrl = paymentService.getCheckoutUrl(payment);

      return ResponseEntity.ok(
          Map.of(
              "payment", payment,
              "checkoutUrl", checkoutUrl));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  @Operation(summary = "Consulta um pagamento")
  @GetMapping("/{id}")
  public ResponseEntity<?> getById(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(paymentService.getById(id));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
  }

  @Operation(
      summary = "[SIMULADO] Tela de checkout fake para testar o fluxo sem um gateway real",
      description =
          "Só existe enquanto usamos o FakePaymentGateway. Some quando trocarmos por um provedor"
              + " de verdade (o cliente seria redirecionado pro checkout do provedor).")
  @GetMapping(value = "/{id}/fake-checkout", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> fakeCheckout(@PathVariable Long id) {
    Payment payment;
    try {
      payment = paymentService.getById(id);
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Pagamento não encontrado.");
    }

    // Depois de aprovar/recusar, o usuário volta para o site (na mesma aba), no painel certo.
    boolean assinatura = payment.getType() == PaymentType.ASSINATURA;
    String painel = assinatura ? "/dashboard-profissional" : "/dashboard-cliente";
    String tipo = assinatura ? "assinatura" : "demanda";
    String site =
        frontendUrl.endsWith("/")
            ? frontendUrl.substring(0, frontendUrl.length() - 1)
            : frontendUrl;
    String voltar = site + painel;
    String retorno = voltar + "?tipo=" + tipo + "&pagamento=";

    String html =
        """
        <!DOCTYPE html>
        <html lang="pt-br">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Checkout simulado - ConectaPro</title>
          <style>
            body { font-family: sans-serif; max-width: 420px; margin: 60px auto; padding: 0 16px; text-align: center; }
            .badge { background: #fef3c7; color: #92400e; padding: 4px 10px; border-radius: 6px; font-size: 12px; }
            .valor { font-size: 32px; font-weight: bold; margin: 20px 0; }
            button { padding: 12px 20px; margin: 8px; border: none; border-radius: 8px; font-size: 16px; cursor: pointer; }
            button:disabled { opacity: .6; cursor: default; }
            .aprovar { background: #16a34a; color: white; }
            .recusar { background: #dc2626; color: white; }
            .voltar { display: inline-block; margin-top: 18px; color: #475569; font-size: 14px; }
          </style>
        </head>
        <body>
          <span class="badge">MODO SIMULADO — nenhum dinheiro real será cobrado</span>
          <h2>Pagamento #__ID__</h2>
          <div class="valor">R$ __VALOR__</div>
          <p>Status atual: <strong>__STATUS__</strong></p>
          <button class="aprovar" onclick="enviar('aprovar')">Aprovar pagamento</button>
          <button class="recusar" onclick="enviar('recusar')">Recusar pagamento</button>
          <p id="resultado"></p>
          <a class="voltar" href="__VOLTAR__">&larr; Voltar sem pagar</a>
          <script>
            async function enviar(acao) {
              document.querySelectorAll('button').forEach(b => b.disabled = true);
              const resultado = document.getElementById('resultado');
              try {
                const resp = await fetch('/api/payments/__ID__/' + acao, { method: 'POST' });
                if (resp.ok) {
                  resultado.innerText = acao === 'aprovar'
                    ? 'Pagamento aprovado! Voltando para o site...'
                    : 'Pagamento recusado. Voltando para o site...';
                  const status = acao === 'aprovar' ? 'aprovado' : 'recusado';
                  setTimeout(() => { window.location.href = '__RETORNO__' + status; }, 1200);
                } else {
                  resultado.innerText = 'Erro: ' + (await resp.text());
                  document.querySelectorAll('button').forEach(b => b.disabled = false);
                }
              } catch (e) {
                resultado.innerText = 'Erro de conexão. Tente novamente.';
                document.querySelectorAll('button').forEach(b => b.disabled = false);
              }
            }
          </script>
        </body>
        </html>
        """
            .replace("__ID__", String.valueOf(payment.getId()))
            .replace("__VALOR__", "%.2f".formatted(payment.getAmount()))
            .replace("__STATUS__", String.valueOf(payment.getStatus()))
            .replace("__VOLTAR__", voltar)
            .replace("__RETORNO__", retorno);

    return ResponseEntity.ok(html);
  }

  @Operation(summary = "[SIMULADO] Aprova o pagamento manualmente")
  @PostMapping("/{id}/aprovar")
  public ResponseEntity<?> aprovar(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(paymentService.aprovar(id));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  @Operation(summary = "[SIMULADO] Recusa o pagamento manualmente")
  @PostMapping("/{id}/recusar")
  public ResponseEntity<?> recusar(@PathVariable Long id) {
    try {
      return ResponseEntity.ok(paymentService.recusar(id));
    } catch (NoSuchElementException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }
}

package br.com.conectaPro.security;

import br.com.conectaPro.model.demand.Demand;
import br.com.conectaPro.model.subscription.Subscription;
import jakarta.annotation.PreDestroy;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Envio de e-mails. O texto é montado na thread da requisição (que ainda tem acesso às entidades
 * JPA), mas o envio SMTP acontece em segundo plano: assim aceitar/recusar/pagar não fica esperando
 * o servidor de e-mail responder. Falhas são registradas no log e nunca derrubam o fluxo
 * principal.
 */
@Service
@RequiredArgsConstructor
public class EmailService {

  private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final JavaMailSender mailSender;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @Value("${spring.mail.username}")
  private String from;

  /** URL base do site (front), usada nos links dos e-mails. */
  @Value("${app.frontend.url:http://localhost:5173}")
  private String frontendUrl;

  @PreDestroy
  void encerrar() {
    executor.shutdown();
  }

  private void enviar(String para, String assunto, String corpo) {
    if (para == null || para.isBlank()) {
      return;
    }
    executor.submit(
        () -> {
          try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "ConectaPro");
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(corpo, true); // true = HTML habilitado
            mailSender.send(message);
            System.out.println("E-mail enviado: \"" + assunto + "\" -> " + para);
          } catch (Exception e) {
            System.err.println("Erro ao enviar e-mail para " + para + ": " + e.getMessage());
          }
        });
  }

  private String siteUrl() {
    return frontendUrl.endsWith("/")
        ? frontendUrl.substring(0, frontendUrl.length() - 1)
        : frontendUrl;
  }

  /** Escapa texto digitado por usuários antes de colocar no HTML do e-mail. */
  private static String esc(String texto) {
    return HtmlUtils.htmlEscape(texto == null ? "" : texto);
  }

  private static String linha(String rotulo, String valor) {
    return "<tr><td><strong>%s:</strong></td><td>%s</td></tr>".formatted(rotulo, valor);
  }

  private static String tabelaDemanda(Demand demand, String... extras) {
    StringBuilder sb = new StringBuilder("<table>");
    sb.append(linha("Código", esc(demand.getCode())));
    sb.append(linha("Serviço", esc(demand.getTitle())));
    for (int i = 0; i + 1 < extras.length; i += 2) {
      sb.append(linha(extras[i], extras[i + 1]));
    }
    return sb.append("</table>").toString();
  }

  // ---------------------------------------------------------------------------------------------
  // Conta
  // ---------------------------------------------------------------------------------------------

  public void sendRecoveryEmail(String to, String token) {
    String link = siteUrl() + "/reset-password?token=" + token;
    String corpo =
        """
        <p>Você solicitou a recuperação de senha na <strong>ConectaPro</strong>.</p>
        <p>Clique no link abaixo para redefinir sua senha:</p>
        <p><a href="%s">%s</a></p>
        <p>O link expira em <strong>30 minutos</strong> e só pode ser usado uma vez.</p>
        <p>Caso não tenha solicitado esta alteração, ignore este e-mail: sua senha continua a mesma.</p>
        """
            .formatted(link, link);
    enviar(to, "Recuperação de senha - ConectaPro", corpo);
  }

  /** Confirmação de segurança enviada depois que a senha é alterada. */
  public void sendPasswordChangedEmail(String to, String nome) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>A senha da sua conta na <strong>ConectaPro</strong> foi alterada com sucesso.</p>
        <p>Se foi você, não precisa fazer nada.</p>
        <p><strong>Se você não fez essa alteração</strong>, redefina sua senha imediatamente em
        <a href="%s/esqueceu-senha">%s/esqueceu-senha</a> e entre em contato conosco.</p>
        """
            .formatted(esc(nome), siteUrl(), siteUrl());
    enviar(to, "Sua senha foi alterada - ConectaPro", corpo);
  }

  // ---------------------------------------------------------------------------------------------
  // Demanda (um e-mail por mudança de status)
  // ---------------------------------------------------------------------------------------------

  /** ABERTO: avisa o profissional que chegou um pedido. */
  public void notificarNovaDemanda(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>Você recebeu uma nova solicitação de serviço na <strong>ConectaPro</strong>.</p>
        %s
        <p>Acesse o app para aceitar ou recusar. Você tem <strong>1 hora</strong> para responder antes que a demanda expire.</p>
        """
            .formatted(
                esc(demand.getProfessionalId().getName()),
                tabelaDemanda(demand, "Cliente", esc(demand.getClientId().getName())));
    enviar(
        demand.getProfessionalId().getEmail(),
        "Nova solicitação de serviço [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  /** AGUARDANDO_PAGAMENTO: avisa o cliente que o profissional aceitou e definiu o valor. */
  public void notificarAguardandoPagamento(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>O profissional <strong>%s</strong> aceitou sua solicitação e definiu o valor do serviço.</p>
        %s
        <p>Acesse o app para confirmar e efetuar o pagamento.</p>
        """
            .formatted(
                esc(demand.getClientId().getName()),
                esc(demand.getProfessionalId().getName()),
                tabelaDemanda(demand, "Valor", "R$ %.2f".formatted(demand.getFinalValue())));
    enviar(
        demand.getClientId().getEmail(),
        "Valor definido, falta pagar [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  /** AGUARDANDO (pagamento aprovado): confirma para o cliente e libera o contato. */
  public void notificarDemandaAceita(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>Pagamento confirmado! O profissional <strong>%s</strong> já pode executar o serviço e o contato dele foi liberado no app.</p>
        %s
        <p>Acompanhe o andamento pelo app.</p>
        """
            .formatted(
                esc(demand.getClientId().getName()),
                esc(demand.getProfessionalId().getName()),
                tabelaDemanda(demand, "Status", "Aguardando execução"));
    enviar(
        demand.getClientId().getEmail(),
        "Pagamento confirmado [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  /** AGUARDANDO (pagamento aprovado): avisa o profissional que pode começar. */
  public void notificarPagamentoAprovadoProfissional(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>O cliente <strong>%s</strong> pagou o serviço. Você já pode combinar e executar o trabalho; o contato do cliente foi liberado no app.</p>
        %s
        """
            .formatted(
                esc(demand.getProfessionalId().getName()),
                esc(demand.getClientId().getName()),
                tabelaDemanda(demand, "Valor", "R$ %.2f".formatted(demand.getFinalValue())));
    enviar(
        demand.getProfessionalId().getEmail(),
        "Pagamento recebido [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  /** Pagamento recusado: avisa o cliente (a demanda continua aguardando pagamento). */
  public void notificarPagamentoRecusado(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>O pagamento da sua solicitação não foi aprovado.</p>
        %s
        <p>Nada foi cobrado. Acesse o app para tentar pagar novamente.</p>
        """
            .formatted(esc(demand.getClientId().getName()), tabelaDemanda(demand));
    enviar(
        demand.getClientId().getEmail(),
        "Pagamento não aprovado [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  /** REJEITADO: avisa o cliente. */
  public void notificarDemandaRejeitada(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>Infelizmente o profissional <strong>%s</strong> não pôde atender sua solicitação.</p>
        %s
        <p>Acesse o app para escolher outro profissional.</p>
        """
            .formatted(
                esc(demand.getClientId().getName()),
                esc(demand.getProfessionalId().getName()),
                tabelaDemanda(demand));
    enviar(
        demand.getClientId().getEmail(),
        "Solicitação recusada [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  /** FECHADO: avisa o cliente para avaliar. */
  public void notificarDemandaFechada(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>O serviço realizado por <strong>%s</strong> foi concluído.</p>
        %s
        <p>Que tal avaliar o profissional? Sua opinião ajuda a comunidade ConectaPro!</p>
        <p>Acesse o app para deixar sua avaliação.</p>
        """
            .formatted(
                esc(demand.getClientId().getName()),
                esc(demand.getProfessionalId().getName()),
                tabelaDemanda(demand));
    enviar(
        demand.getClientId().getEmail(),
        "Serviço concluído, avalie! [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  /** EXPIRADO: avisa o cliente. */
  public void notificarDemandaExpirada(Demand demand) {
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>Sua solicitação para o profissional <strong>%s</strong> expirou pois ele não respondeu em 1 hora.</p>
        %s
        <p>Acesse o app para escolher outro profissional e reabrir a solicitação.</p>
        """
            .formatted(
                esc(demand.getClientId().getName()),
                esc(demand.getProfessionalId().getName()),
                tabelaDemanda(demand));
    enviar(
        demand.getClientId().getEmail(),
        "Solicitação expirada [%s] - ConectaPro".formatted(demand.getCode()),
        corpo);
  }

  // ---------------------------------------------------------------------------------------------
  // Assinatura
  // ---------------------------------------------------------------------------------------------

  public void notificarAssinaturaAtivada(Subscription subscription) {
    String plano = subscription.getPlan().getName();
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>Seu plano <strong>%s</strong> foi ativado. Seu perfil já aparece com o selo de verificado e com prioridade na busca.</p>
        <p>Válido até <strong>%s</strong>.</p>
        """
            .formatted(
                esc(subscription.getProfessional().getName()),
                esc(plano),
                subscription.getExpiresAt().format(DATA));
    enviar(
        subscription.getProfessional().getEmail(),
        "Plano %s ativado - ConectaPro".formatted(plano),
        corpo);
  }

  public void notificarAssinaturaExpirada(Subscription subscription) {
    String plano = subscription.getPlan().getName();
    String corpo =
        """
        <p>Olá, <strong>%s</strong>!</p>
        <p>Seu plano <strong>%s</strong> expirou e os benefícios (selo, prioridade na busca e taxa reduzida) foram desativados.</p>
        <p>Para recuperá-los, assine novamente em <a href="%s/planos">%s/planos</a>.</p>
        """
            .formatted(
                esc(subscription.getProfessional().getName()),
                esc(plano),
                siteUrl(),
                siteUrl());
    enviar(
        subscription.getProfessional().getEmail(),
        "Seu plano %s expirou - ConectaPro".formatted(plano),
        corpo);
  }
}

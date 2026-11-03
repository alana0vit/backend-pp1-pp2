package br.com.conectaPro.Chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.conectaPro.model.chat.KnowledgeBase;
import br.com.conectaPro.model.chat.KnowledgeIndex;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KnowledgeBaseTest {

  private final KnowledgeBase kb = new KnowledgeBase();

  @Test
  @DisplayName("Carrega os guias e as 7 categorias do CategorySeeder")
  void carregaGuiasECategorias() {
    assertTrue(kb.size() >= 20);
    assertEquals(
        List.of(
            "Carpinteiro",
            "Eletricista",
            "Encanador",
            "Pedreiro",
            "Pintor",
            "Professor",
            "Serviços Gerais"),
        kb.categories());
  }

  @ParameterizedTest(name = "\"{0}\" -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "meu chuveiro queimou e não esquenta|categoria-eletricista",
        "o disjuntor fica desarmando|categoria-eletricista",
        "tem um vazamento na pia da cozinha|categoria-encanador",
        "rachadura na parede|categoria-pedreiro",
        "parede com mofo descascando|categoria-pintor",
        "preciso de um guarda-roupa sob medida|categoria-carpinteiro",
        "aula de inglês particular|categoria-professor",
        "preciso de uma diarista|categoria-servicos-gerais",
        "quanto custa o plano ouro?|planos-assinatura",
        "qual a taxa da plataforma?|pagamento-taxa",
        "quanto o profissional recebe do que eu pago|pagamento-taxa",
        "o cadastro é pago?|cadastro",
        "quando libera o whatsapp?|contato-liberado",
        "o profissional não respondeu meu pedido|resposta-prazo",
        "o que significa aguardando pagamento|status-pedido",
        "meu pagamento foi recusado|cancelamento-problemas",
        "minha assinatura venceu|como-assinar",
        "mostrar profissionais próximos no mapa|busca-mapa"
      })
  void achaOGuiaCerto(String pergunta, String idEsperado) {
    List<KnowledgeIndex.Hit> hits = kb.search(pergunta, 3);
    assertFalse(hits.isEmpty());
    assertEquals(idEsperado, hits.get(0).doc().id());
  }

  @ParameterizedTest(name = "fora do assunto: \"{0}\"")
  @CsvSource({"qual a capital da França", "como faço um bolo de chocolate", "me conta uma piada"})
  void foraDoAssuntoFicaAbaixoDoLimite(String pergunta) {
    List<KnowledgeIndex.Hit> hits = kb.search(pergunta, 1);
    double melhor = hits.isEmpty() ? 0 : hits.get(0).score();
    assertTrue(melhor < 3.0, "score " + melhor + " não deveria passar o limite padrão (3.0)");
  }

  @Test
  @DisplayName("parse lê titulo/categoria/palavras e o corpo")
  void parseDoArquivo() {
    var doc =
        KnowledgeBase.parse("x", "titulo: T\ncategoria: C\npalavras: a b\n\nCorpo\nlinha 2\n");
    assertEquals("T", doc.title());
    assertEquals("C", doc.category());
    assertEquals("a b", doc.keywords());
    assertEquals("Corpo\nlinha 2", doc.body());
  }
}

package br.com.conectaPro.model.chat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Base de conhecimento do chatbot: carrega os guias de {@code classpath:kb/*.md} na subida da
 * aplicação. Para atualizar o que o bot sabe, basta editar/criar um .md nessa pasta.
 *
 * <p>Formato de cada arquivo: linhas de cabeçalho {@code titulo:}, {@code categoria:} (opcional) e
 * {@code palavras:}, uma linha em branco, e o texto do guia.
 */
@Component
public class KnowledgeBase {

  private final KnowledgeIndex index;

  @Autowired
  public KnowledgeBase() {
    this(carregarDoClasspath());
  }

  public KnowledgeBase(List<KnowledgeDoc> docs) {
    this.index = new KnowledgeIndex(docs);
  }

  public List<KnowledgeIndex.Hit> search(String query, int limit) {
    return index.search(query, limit);
  }

  /** Nomes das categorias de serviço que têm ficha na base. */
  public List<String> categories() {
    return index.documents().stream()
        .map(KnowledgeDoc::category)
        .filter(c -> c != null && !c.isBlank())
        .sorted()
        .toList();
  }

  public int size() {
    return index.documents().size();
  }

  /** Interpreta o conteúdo de um arquivo .md da base. */
  public static KnowledgeDoc parse(String id, String content) {
    String titulo = id;
    String categoria = null;
    String palavras = "";
    StringBuilder corpo = new StringBuilder();
    boolean noCabecalho = true;

    for (String linha : content.replace("\r\n", "\n").split("\n", -1)) {
      if (noCabecalho) {
        if (linha.isBlank()) {
          noCabecalho = false;
        } else if (linha.startsWith("titulo:")) {
          titulo = linha.substring("titulo:".length()).trim();
        } else if (linha.startsWith("categoria:")) {
          categoria = linha.substring("categoria:".length()).trim();
        } else if (linha.startsWith("palavras:")) {
          palavras = linha.substring("palavras:".length()).trim();
        } else {
          noCabecalho = false;
          corpo.append(linha).append('\n');
        }
      } else {
        corpo.append(linha).append('\n');
      }
    }
    return new KnowledgeDoc(id, titulo, categoria, palavras, corpo.toString().trim());
  }

  private static List<KnowledgeDoc> carregarDoClasspath() {
    List<KnowledgeDoc> docs = new ArrayList<>();
    try {
      Resource[] recursos =
          new PathMatchingResourcePatternResolver().getResources("classpath:kb/*.md");
      Arrays.sort(recursos, Comparator.comparing(r -> String.valueOf(r.getFilename())));
      for (Resource recurso : recursos) {
        String nome = String.valueOf(recurso.getFilename());
        try (InputStream in = recurso.getInputStream()) {
          String conteudo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
          docs.add(parse(nome.replaceFirst("\\.md$", ""), conteudo));
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Não foi possível carregar a base de conhecimento.", e);
    }
    System.out.println("Base de conhecimento do chatbot carregada: " + docs.size() + " guias.");
    return docs;
  }
}

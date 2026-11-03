package br.com.conectaPro.model.chat;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Índice de busca BM25 em memória sobre os documentos da base de conhecimento. Sem dependências
 * externas: com algumas dezenas de guias, é rápido e dispensa banco vetorial.
 *
 * <p>Para tolerar variações de escrita ("esquenta"/"esquentar", "queimou"/"queimada"), os termos
 * são normalizados (sem acento, minúsculos, plural removido) e reduzidos aos 5 primeiros
 * caracteres.
 */
public class KnowledgeIndex {

  public record Hit(KnowledgeDoc doc, double score) {}

  private static final double K1 = 1.5;
  private static final double B = 0.75;
  private static final int STEM_LENGTH = 5;

  private static final Set<String> STOPWORDS =
      Set.of(
          "a",
          "o",
          "as",
          "os",
          "um",
          "uma",
          "uns",
          "umas",
          "de",
          "do",
          "da",
          "dos",
          "das",
          "em",
          "no",
          "na",
          "nos",
          "nas",
          "por",
          "para",
          "pra",
          "com",
          "sem",
          "que",
          "e",
          "ou",
          "se",
          "ao",
          "aos",
          "como",
          "qual",
          "quais",
          "quando",
          "onde",
          "quem",
          "meu",
          "minha",
          "meus",
          "minhas",
          "seu",
          "sua",
          "seus",
          "suas",
          "eu",
          "me",
          "te",
          "voce",
          "vc",
          "ele",
          "ela",
          "isso",
          "isto",
          "esse",
          "essa",
          "este",
          "esta",
          "tem",
          "ter",
          "ser",
          "sao",
          "foi",
          "vai",
          "vou",
          "posso",
          "pode",
          "quero",
          "preciso",
          "gostaria",
          "mais",
          "muito",
          "ja",
          "nao",
          "sim",
          "ai",
          "oi",
          "ola",
          "ate",
          "so",
          "mas",
          "tambem",
          "sobre",
          "ainda",
          "algum",
          "alguma",
          "fazer",
          "ficou",
          "estou",
          "estao",
          "tenho",
          "queria",
          "favor");

  private final List<KnowledgeDoc> docs;
  private final List<Map<String, Integer>> termFreqs = new ArrayList<>();
  private final List<Integer> lengths = new ArrayList<>();
  private final Map<String, Integer> docFreq = new HashMap<>();
  private final double avgLength;

  public KnowledgeIndex(List<KnowledgeDoc> docs) {
    this.docs = List.copyOf(docs);
    long total = 0;
    for (KnowledgeDoc doc : this.docs) {
      // Título e palavras-chave pesam mais: entram duas vezes.
      List<String> tokens = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        tokens.addAll(tokenize(doc.title()));
        tokens.addAll(tokenize(doc.keywords()));
      }
      tokens.addAll(tokenize(doc.body()));

      Map<String, Integer> tf = new HashMap<>();
      for (String t : tokens) {
        tf.merge(t, 1, Integer::sum);
      }
      for (String t : tf.keySet()) {
        docFreq.merge(t, 1, Integer::sum);
      }
      termFreqs.add(tf);
      lengths.add(tokens.size());
      total += tokens.size();
    }
    this.avgLength = this.docs.isEmpty() ? 1.0 : (double) total / this.docs.size();
  }

  public List<KnowledgeDoc> documents() {
    return docs;
  }

  /** Retorna até {@code limit} documentos com score maior que zero, do mais ao menos relevante. */
  public List<Hit> search(String query, int limit) {
    Set<String> queryTerms = new LinkedHashSet<>(tokenize(query));
    int n = docs.size();
    List<Hit> hits = new ArrayList<>();

    for (int i = 0; i < n; i++) {
      Map<String, Integer> tf = termFreqs.get(i);
      double norm = 1 - B + B * lengths.get(i) / avgLength;
      double score = 0;
      for (String term : queryTerms) {
        Integer freq = tf.get(term);
        if (freq == null) {
          continue;
        }
        int df = docFreq.get(term);
        double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
        score += idf * (freq * (K1 + 1)) / (freq + K1 * norm);
      }
      if (score > 0) {
        hits.add(new Hit(docs.get(i), score));
      }
    }

    hits.sort(Comparator.comparingDouble(Hit::score).reversed());
    return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
  }

  /** Minúsculas, sem acentos, sem stopwords, sem plural, reduzido a 5 caracteres. */
  public static List<String> tokenize(String text) {
    List<String> result = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return result;
    }
    String normalized =
        Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    for (String raw : normalized.split("[^a-z0-9]+")) {
      if (raw.isEmpty() || STOPWORDS.contains(raw)) {
        continue;
      }
      if (raw.length() < 2 && !Character.isDigit(raw.charAt(0))) {
        continue;
      }
      String t = raw;
      if (t.length() > 3 && t.endsWith("s")) {
        t = t.substring(0, t.length() - 1);
      }
      if (t.length() > STEM_LENGTH) {
        t = t.substring(0, STEM_LENGTH);
      }
      result.add(t);
    }
    return result;
  }
}

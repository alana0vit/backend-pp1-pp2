package br.com.conectaPro.model.chat;

/**
 * Um documento da base de conhecimento do chatbot (um arquivo .md em src/main/resources/kb).
 *
 * @param id nome do arquivo sem extensão
 * @param title título (campo "titulo:")
 * @param category nome da categoria de serviço relacionada, quando houver (campo "categoria:")
 * @param keywords sinônimos e termos do dia a dia que ajudam a achar o guia (campo "palavras:")
 * @param body texto do guia
 */
public record KnowledgeDoc(
    String id, String title, String category, String keywords, String body) {}

package br.com.conectaPro.util;

import java.time.LocalDate;
import java.time.Period;

/** Regras de idade usadas no cadastro. */
public final class AgeUtils {

  public static final int IDADE_MINIMA = 18;

  private AgeUtils() {}

  /** true se, em {@code hoje}, a pessoa nascida em {@code nascimento} já fez 18 anos. */
  public static boolean isMaiorDeIdade(LocalDate nascimento, LocalDate hoje) {
    if (nascimento == null || nascimento.isAfter(hoje)) {
      return false;
    }
    return Period.between(nascimento, hoje).getYears() >= IDADE_MINIMA;
  }
}

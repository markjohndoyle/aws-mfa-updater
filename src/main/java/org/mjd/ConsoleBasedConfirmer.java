package org.mjd;

import java.io.Console;
import java.util.function.Function;
import javax.inject.Singleton;

@Singleton
final class ConsoleBasedConfirmer implements Function<String, Boolean> {

  private final Console console = System.console();

  @Override
  public Boolean apply(String question) {
    System.out.print(question);
    var answer = console.readLine();
    return answer.isEmpty() || answer.equalsIgnoreCase("Y");
  }
}

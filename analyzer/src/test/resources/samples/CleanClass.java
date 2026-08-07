package samples;

public class CleanClass {

  private final String name;

  public CleanClass(String name) {
    this.name = name;
  }

  public String displayName() {
    return name.toUpperCase();
  }
}

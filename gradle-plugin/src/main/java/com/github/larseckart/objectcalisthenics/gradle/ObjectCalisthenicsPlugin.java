package com.github.larseckart.objectcalisthenics.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle plugin that analyses Java source code for Object Calisthenics
 * violations.
 *
 * <p>The plugin registers two tasks:</p>
 *
 * <ul>
 *   <li>{@code objectCalisthenicsCheck} – fails the build if violations are found</li>
 *   <li>{@code objectCalisthenicsReport} – writes a JSON report without failing</li>
 * </ul>
 */
public class ObjectCalisthenicsPlugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    ObjectCalisthenicsExtension extension = project.getExtensions()
        .create("objectCalisthenics", ObjectCalisthenicsExtension.class);

    extension.getReports().getJson().convention(
        project.getLayout().getBuildDirectory().file("reports/calisthenics/calisthenics.json")
    );
    extension.getBaselineFile().convention(
        project.getLayout().getProjectDirectory().file("object-calisthenics-baseline.txt")
    );

    project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
      SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
      extension.getSourceSet().convention(sourceSets.getByName("main"));
    });

    TaskProvider<ObjectCalisthenicsCheckTask> checkTask = project.getTasks()
        .register("objectCalisthenicsCheck", ObjectCalisthenicsCheckTask.class, task -> {
          task.setGroup("Verification");
          task.setDescription("Checks the project for Object Calisthenics violations.");
          task.getSourceFiles().setFrom(extension.getSourceSet().map(this::allJava));
          linkRules(task.getRules(), extension.getRules());
          task.getClassNamePatterns().set(extension.getExclusions().getClassNamePatterns());
          task.getConsoleSummary().set(extension.getReports().getConsoleSummary());
          task.getBaselineFile().set(extension.getBaselineFile());
          task.getBaselineFiles().setFrom(extension.getBaselineFile());
          task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
          task.getIgnoreFailures().set(extension.getIgnoreFailures());
        });

    TaskProvider<ObjectCalisthenicsReportTask> reportTask = project.getTasks()
        .register("objectCalisthenicsReport", ObjectCalisthenicsReportTask.class, task -> {
          task.setGroup("Reporting");
          task.setDescription("Writes an Object Calisthenics JSON report.");
          task.getSourceFiles().setFrom(extension.getSourceSet().map(this::allJava));
          linkRules(task.getRules(), extension.getRules());
          task.getClassNamePatterns().set(extension.getExclusions().getClassNamePatterns());
          task.getJson().set(extension.getReports().getJson());
          task.getConsoleSummary().set(extension.getReports().getConsoleSummary());
        });

    TaskProvider<ObjectCalisthenicsBaselineTask> baselineTask = project.getTasks().register(
        "objectCalisthenicsBaseline",
        ObjectCalisthenicsBaselineTask.class,
        task -> {
          task.setGroup("Verification");
          task.setDescription("Creates an Object Calisthenics baseline from current violations.");
          task.getSourceFiles().setFrom(extension.getSourceSet().map(this::allJava));
          linkRules(task.getRules(), extension.getRules());
          task.getClassNamePatterns().set(extension.getExclusions().getClassNamePatterns());
          task.getBaselineFile().set(extension.getBaselineFile());
          task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
        }
    );

    checkTask.configure(task -> task.mustRunAfter(baselineTask));
    project.getTasks().named("check").configure(check -> check.dependsOn(checkTask));
  }

  private Object allJava(SourceSet sourceSet) {
    return sourceSet.getAllJava();
  }

  private void linkRules(ObjectCalisthenicsRules target, ObjectCalisthenicsRules source) {
    target.getMaxClassLines().set(source.getMaxClassLines());
    target.getMaxFieldsPerClass().set(source.getMaxFieldsPerClass());
    target.getIncludeRecordComponentsInFieldRule().set(source.getIncludeRecordComponentsInFieldRule());
    target.getForbidElse().set(source.getForbidElse());
    target.getMaxMethodNesting().set(source.getMaxMethodNesting());
    target.getForbidGetters().set(source.getForbidGetters());
    target.getForbidSetters().set(source.getForbidSetters());
    target.getForbidNonFirstClassCollections().set(source.getForbidNonFirstClassCollections());
    target.getStrictGetterNames().set(source.getStrictGetterNames());
    target.getForbidTraversalChains().set(source.getForbidTraversalChains());
    target.getFluentChainMethods().set(source.getFluentChainMethods());
    target.getSafeChainRoots().set(source.getSafeChainRoots());
  }
}

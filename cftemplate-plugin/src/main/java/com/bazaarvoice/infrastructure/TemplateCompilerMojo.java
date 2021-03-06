package com.bazaarvoice.infrastructure;

import com.bazaarvoice.infrastructure.cftemplate.CompileIssue;
import com.bazaarvoice.infrastructure.cftemplate.CompileIssueLevel;
import com.bazaarvoice.infrastructure.cftemplate.CompileResult;
import com.bazaarvoice.infrastructure.cftemplate.JsonTemplateCompiler;
import com.bazaarvoice.infrastructure.cftemplate.RubyTemplateCompiler;
import com.bazaarvoice.infrastructure.cftemplate.TemplateCompiler;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newArrayListWithCapacity;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

/**
 * Goal which compiles CloudFormation templates.
 * <p/>
 * Compiles JSON and Ruby DSL templates to CloudFormation JSON. Performs validations
 * on the resulting template to ensure correctness.
 *
 * @goal cftemplates
 * @phase process-resources
 */
public class TemplateCompilerMojo
        extends AbstractMojo {
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter default-value="${project.build.directory}/cftemplate-compiler"
     * @required
     * @readonly
     */
    private File stateDir;

    /**
     * Directory to load templates from.
     *
     * @parameter expression="${basedir}/src/main/cftemplates"
     * @required
     */
    private File inputDirectory;

    /**
     * Directory to store the compiled templates in.
     *
     * @parameter expression="${project.build.directory}/processed-resources/cftemplates"
     * @required
     */
    private File outputDirectory;

    /**
     * Set of case-insensitive patterns for files to include in compilation. Default is *stack.rb and *stack.json.
     *
     * @parameter
     */
    private Set<String> includes = newHashSet("*stack.rb", "*stack.json");

    /**
     * Set of case-insensitive patterns for files to exclude from compilation. Default is no excludes.
     *
     * @parameter
     */
    private Set<String> excludes = newHashSet();

    /**
     * Template parameter default value overrides.
     *
     * @parameter
     */
    private Map<String, String> parameters = newHashMap();

    private RubyTemplateCompiler _rubyTemplateCompiler = new RubyTemplateCompiler();
    private JsonTemplateCompiler _jsonTemplateCompiler = new JsonTemplateCompiler();

    public void execute()
            throws MojoExecutionException {
        if (!inputDirectory.isDirectory()) {
            info("No templates found in %s", inputDirectory);
            return;
        }

        _rubyTemplateCompiler.setParameters(parameters);
        _jsonTemplateCompiler.setParameters(parameters);

        File outDir = getOutputDirectory();
        List<File> sourceFiles = walk(inputDirectory, new GlobFilenameFilter(includes, excludes));
        List<Compilation> compiles = newArrayListWithCapacity(sourceFiles.size());

        for (File file : sourceFiles) {
            String extension = FilenameUtils.getExtension(file.getName());
            File outputFile = changeExtension(changeBaseDir(inputDirectory, outDir, file), ".json");
            TemplateCompiler compiler = null;

            if (extension.equals("rb")) {
                compiler = _rubyTemplateCompiler;
            } else if (extension.equals("json")) {
                compiler = _jsonTemplateCompiler;
            } else {
                warn("Unknown CloudFormation template type: %s", file);
            }

            if (compiler != null) {
                compiles.add(new Compilation(file, outputFile, compiler));
            }
        }

        if (compiles.size() == 0) {
            info("No templates to compile in %s", inputDirectory);
        } else {
            info("Compiling %d CloudFormation templates to %s", compiles.size(), outDir);
            int failures = 0;

            for (Compilation c : compiles) {
                info("Compiling %s to %s", c.sourceFile, c.targetFile);

                try {
                    CompileResult result = c.compile();
                    failures += outputResults(result);
                } catch (IOException ex) {
                    throw new MojoExecutionException(String.format("Error compiling %s", c.sourceFile), ex);
                }
            }

            if (failures > 0) {
                throw new MojoExecutionException(String.format("%d errors compiling CloudFormation templates", failures));
            }
        }
    }

    private static File changeBaseDir(File oldBaseDir, File newBaseDir, File path) {
        String subPath = path.getAbsolutePath().substring(oldBaseDir.getAbsolutePath().length() + 1);
        return new File(newBaseDir, subPath);
    }

    private static void walk(File directory, FilenameFilter filter, List<File> files) {
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                walk(file, filter, files);
            } else if (filter.accept(directory, file.getName())) {
                files.add(file);
            }
        }
    }

    private static List<File> walk(File directory, FilenameFilter filter) {
        List<File> files = newArrayList();
        walk(directory, filter, files);
        return files;
    }

    private int outputResults(CompileResult result) {
        int failureCount = 0;

        for (CompileIssue issue : result.getIssues()) {
            CompileIssueLevel level = issue.getLevel();
            String message;

            if (issue.getLocation() != null) {
                message = String.format("%s\n%s", issue.getLocation(), issue.getMessage());
            } else {
                message = issue.getMessage();
            }

            if (level.compareTo(CompileIssueLevel.ERROR) >= 0) {
                failureCount += 1;
                error(message);
            } else if (level.compareTo(CompileIssueLevel.WARN) >= 0) {
                warn(message);
            } else if (level.compareTo(CompileIssueLevel.INFO) >= 0) {
                info(message);
            } else {
                debug(message);
            }
        }

        return failureCount;
    }

    private static File changeExtension(File path, String extension) {
        String name = path.getName();
        int dotIndex = name.lastIndexOf('.');

        if (dotIndex < 0) {
            name = name + extension;
        } else if (dotIndex == 0) {
            name = extension;
        } else {
            name = name.substring(0, dotIndex) + extension;
        }

        return new File(path.getParent(), name);
    }

    private File getOutputDirectory() {
        createDirectory(outputDirectory);

        boolean found = false;

        for (Resource r : (Iterable<Resource>) project.getResources()) {
            if (r.getDirectory().equals(outputDirectory)) {
                found = true;
            }
        }

        if (!found) {
            Resource resource = new Resource();
            resource.setDirectory(outputDirectory.getPath());
            project.addResource(resource);
        }

        return outputDirectory;
    }

    private static class Compilation {
        public final File sourceFile;
        public final File targetFile;
        public final TemplateCompiler compiler;

        public Compilation(File sourceFile, File targetFile, TemplateCompiler compiler) {
            this.sourceFile = sourceFile;
            this.targetFile = targetFile;
            this.compiler = compiler;
        }

        public CompileResult compile()
                throws IOException {
            return compiler.compile(sourceFile, targetFile);
        }
    }
}

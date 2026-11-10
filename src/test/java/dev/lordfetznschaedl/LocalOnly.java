package dev.lordfetznschaedl;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks tests that need the demo files in {@code src/test/resources/demos}. Those files are not under
 * version control (about 230 MB each), so these tests only run on a developer machine:
 * <ul>
 *     <li>they are disabled on CI servers (GitHub Actions / GitLab / most others set {@code CI},
 *     Jenkins sets {@code JENKINS_URL}, Azure Pipelines sets {@code TF_BUILD})</li>
 *     <li>they carry the tag {@value #TAG}, so they can also be excluded explicitly with
 *     {@code mvn test -DexcludedGroups=local}</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Tag(LocalOnly.TAG)
@DisabledIfEnvironmentVariable(named = "CI", matches = "(?i)true|1|yes", disabledReason = "needs local demo files")
@DisabledIfEnvironmentVariable(named = "JENKINS_URL", matches = ".+", disabledReason = "needs local demo files")
@DisabledIfEnvironmentVariable(named = "TF_BUILD", matches = "(?i)true", disabledReason = "needs local demo files")
public @interface LocalOnly {

    String TAG = "local";
}

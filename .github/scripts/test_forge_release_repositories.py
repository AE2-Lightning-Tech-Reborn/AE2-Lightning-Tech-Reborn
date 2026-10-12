"""Verify raw release pinning leaves ForgeGradle's mapped coordinates resolvable."""

from pathlib import Path
import subprocess
import tempfile
from zipfile import ZipFile


root = Path(__file__).resolve().parents[2]
modules = (
    ("com.moakiee.thunderbolt", "thunderbolt-reborn-forge-1.20.1", "2.0.3-beta"),
    ("com.moakiee.ae2lt", "ae2lt", "2.1.2-beta"),
)


def stage(repository, group, artifact, version, origin):
    folder = repository / group.replace(".", "/") / artifact / version
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{artifact}-{version}.pom").write_text(
        '<project xmlns="http://maven.apache.org/POM/4.0.0">'
        f"<modelVersion>4.0.0</modelVersion><groupId>{group}</groupId>"
        f"<artifactId>{artifact}</artifactId><version>{version}</version></project>",
        encoding="utf-8",
    )
    with ZipFile(folder / f"{artifact}-{version}.jar", "w") as jar:
        jar.writestr("origin.txt", origin)


with tempfile.TemporaryDirectory(prefix="forge-release-repositories-") as directory:
    fixture = Path(directory)
    (fixture / "settings.gradle").write_text("rootProject.name = 'release-repository-test'\n")
    for group, artifact, version in modules:
        stage(fixture / "release-dependencies/maven", group, artifact, version, "release")
        # A competing original must not bypass the validated release repository.
        stage(fixture / "generated", group, artifact, version, "wrong-original")
        stage(fixture / "generated", group, artifact, version + "_mapped_official_1.20.1", "mapped")
    dependencies = "\n".join(
        f"    original '{group}:{artifact}:{version}'\n"
        f"    mapped '{group}:{artifact}:{version}_mapped_official_1.20.1'"
        for group, artifact, version in modules
    )
    (fixture / "build.gradle").write_text(
        """repositories { maven { url = uri('generated') } }
configurations { original; mapped }
dependencies {
""" + dependencies + """
}
tasks.register('verifyReleaseRepositories') {
    doLast {
        [original: (project.findProperty('expectedOriginal') ?: 'release'), mapped: 'mapped'].each { configuration, expected ->
            def resolved = configurations.getByName(configuration).resolve()
            assert resolved.size() == 2
            resolved.each { artifact ->
                def archive = new java.util.zip.ZipFile(artifact)
                try {
                    assert archive.getInputStream(archive.getEntry('origin.txt')).getText('UTF-8') == expected
                } finally {
                    archive.close()
                }
            }
        }
    }
}
""", encoding="utf-8")
    command = [
        "java", "-cp", str(root / "gradle/wrapper/gradle-wrapper.jar"),
        "org.gradle.wrapper.GradleWrapperMain", "--no-daemon", "--offline",
        "--max-workers", "1", "-p", str(fixture),
        "--init-script", str(root / ".github/release-dependencies.init.gradle"),
        "verifyReleaseRepositories",
    ]
    subprocess.run(command, check=True)
    # No staged JARs is the GTL path: generated/default repositories must stay available.
    (fixture / "release-dependencies").rename(fixture / "unused-release-dependencies")
    subprocess.run(command + ["-PexpectedOriginal=wrong-original"], check=True)

allprojects {
    repositories {
        google()
        mavenCentral()
        // Cœur dz-eid publié localement (.\gradlew.bat :dz-eid-core:publish à la racine de dz-eid).
        // Une application cliente pointera ici vers le dépôt Maven privé dz-eid.
        maven { url = uri(rootDir.resolve("../../../../build/maven-repo")) }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

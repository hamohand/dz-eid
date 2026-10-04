plugins {
    application
}

description = "dz-eid agent : pont local sécurisé entre un lecteur PC/SC et les applications web."

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

dependencies {
    implementation(project(":dz-eid-core"))
    implementation("net.sf.scuba:scuba-sc-j2se:0.0.22")
    implementation("io.javalin:javalin:6.7.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    // Décodage JPEG2000 (photo DG2) pour l'affichage navigateur. Licence à valider avant commercialisation.
    implementation("com.github.jai-imageio:jai-imageio-jpeg2000:1.4.0")
}

application {
    mainClass.set("com.muhend.dzeid.agent.AgentMain")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

tasks.jar {
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}

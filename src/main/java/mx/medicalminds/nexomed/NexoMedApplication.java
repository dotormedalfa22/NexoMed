package mx.medicalminds.nexomed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * NexoMed: logística y distribución de insumos médicos.
 * WAR ejecutable: corre con "java -jar" o se despliega en Tomcat 11 / WildFly.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NexoMedApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(NexoMedApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(NexoMedApplication.class);
    }
}

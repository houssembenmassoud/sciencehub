package io.qzz.tbsciencehubproject.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication(scanBasePackages = "io.qzz.tbsciencehubproject")
@EnableJpaRepositories(basePackages = "io.qzz.tbsciencehubproject")
@EntityScan("io.qzz.tbsciencehubproject")
public class ScienceHubApplication {

  public static void main(String[] args) {
    SpringApplication.run(ScienceHubApplication.class, args);
  }

}

package com.example.linuxterminal;

import com.example.linuxterminal.terminal.config.TerminalProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(TerminalProperties.class)
public class LinuxTerminalApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinuxTerminalApplication.class, args);
    }
}

package com.agentic.urlshortener.orchestration.agent;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfig {

    /** Every {@link StageAgent} bean, registered as primary or fallback of its stage type. */
    @Bean
    AgentRegistry agentRegistry(ObjectProvider<StageAgent> agents) {
        return new AgentRegistry(agents.orderedStream().toList());
    }
}

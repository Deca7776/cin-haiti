package ht.oni.cin.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Profile("!local")
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        // On part d'une copie de l'ObjectMapper auto-configure par Spring Boot (module JSR-310 deja
        // enregistre, donc java.time.Instant/LocalDate se serialisent correctement) plutot que du
        // constructeur sans argument de GenericJackson2JsonRedisSerializer.
        // Mais ce dernier active par defaut le "default typing" (embarque "@class" dans le JSON) pour
        // pouvoir reconstruire le bon type Java a la lecture (ex: SessionService.SessionData) ; l'
        // ObjectMapper de Spring Boot ne l'active pas. Sans ce typage, la lecture depuis Redis renvoie
        // un LinkedHashMap generique au lieu du type attendu -> ClassCastException plus loin.
        // On active donc explicitement le meme default typing sur notre copie.
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}

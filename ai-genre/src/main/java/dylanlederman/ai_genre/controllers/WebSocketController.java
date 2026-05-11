package dylanlederman.ai_genre.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocketController {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private SimpMessagingTemplate wsTemplate;

    @SubscribeMapping("/topic/results/{taskId}")
    public void onSubscribe(@DestinationVariable String taskId, SimpMessageHeaderAccessor headerAccessor) {
        String cached = redisTemplate.opsForValue().get("result:" + taskId);
        if (cached != null) {
            wsTemplate.convertAndSend("topic/results/" + taskId, cached);
        }
    }
}

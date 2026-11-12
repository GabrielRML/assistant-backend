package com.assistent.api.conversation;

import com.assistent.api.common.mapper.CentralMapperConfig;
import com.assistent.api.conversation.dto.ConversationResponse;
import com.assistent.api.conversation.dto.ConversationSummaryResponse;
import com.assistent.api.conversation.dto.MessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface ConversationMapper {

    ConversationResponse toResponse(Conversation conversation);

    @Mapping(target = "id", source = "conversation.id")
    @Mapping(target = "lastMessagePreview", source = "lastMessagePreview")
    @Mapping(target = "lastMessageAt", source = "conversation.lastMessageAt")
    ConversationSummaryResponse toSummary(Conversation conversation, String lastMessagePreview);

    MessageResponse toResponse(Message message);
}

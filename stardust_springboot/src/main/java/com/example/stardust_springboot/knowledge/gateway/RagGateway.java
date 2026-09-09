package com.example.stardust_springboot.knowledge.gateway;

import java.util.List;

public interface RagGateway {
    RagProcessResult process(RagProcessCommand command);

    RagRetrieveResult retrieve(String userPublicId, List<String> knowledgeBaseIds, String query);

    void deleteDocument(String userPublicId, String knowledgeBaseId, String documentId);
}

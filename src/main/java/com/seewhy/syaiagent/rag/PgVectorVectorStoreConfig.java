package com.seewhy.syaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

@Configuration
@ConditionalOnProperty(name = "travel.rag.mode", havingValue = "pgvector")
@Slf4j
public class PgVectorVectorStoreConfig {

    private final TravelDocumentLoader documentLoader;

    public PgVectorVectorStoreConfig(TravelDocumentLoader documentLoader) {
        this.documentLoader = documentLoader;
    }

    @Bean
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        try {
            VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .dimensions(1536)
                    .distanceType(COSINE_DISTANCE)
                    .indexType(HNSW)
                    .initializeSchema(true)
                    .schemaName("public")
                    .vectorTableName("vector_store")
                    .maxDocumentBatchSize(10000)
                    .build();
            try {
                vectorStore.add(documentLoader.loadMarkdowns());
            } catch (RuntimeException ex) {
                log.warn("PgVector document preload failed; RAG service will degrade at query time", ex);
            }
            return vectorStore;
        } catch (RuntimeException ex) {
            log.warn("PgVector VectorStore could not be created; RAG service will degrade at query time", ex);
            return new UnavailableVectorStore();
        }
    }

    static class UnavailableVectorStore implements VectorStore {

        @Override
        public void add(List<Document> documents) {
            throw unavailable();
        }

        @Override
        public void delete(List<String> idList) {
            throw unavailable();
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            throw unavailable();
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            throw unavailable();
        }

        @Override
        public <T> Optional<T> getNativeClient() {
            return Optional.empty();
        }

        private IllegalStateException unavailable() {
            return new IllegalStateException("PgVector VectorStore is unavailable");
        }
    }
}

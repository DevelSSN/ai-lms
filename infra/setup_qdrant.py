from qdrant_client import QdrantClient
from qdrant_client.http import models
import os

QDRANT_URL = os.getenv("QDRANT_URL", "http://localhost:6333")
client = QdrantClient(url=QDRANT_URL)

def setup_collections():
    print(f"Connecting to Qdrant at {QDRANT_URL}...")
    
    # Create collection for learning materials
    client.recreate_collection(
        collection_name="learning_material",
        vectors_config=models.VectorParams(size=1536, distance=models.Distance.COSINE),
    )
    
    print("Collection 'learning_material' created.")

    # Insert sample data
    client.upsert(
        collection_name="learning_material",
        points=[
            models.PointStruct(
                id=1,
                vector=[0.1] * 1536,
                payload={"text": "Neural networks are a set of algorithms, modeled loosely after the human brain.", "topic": "AI"}
            ),
            models.PointStruct(
                id=2,
                vector=[0.2] * 1536,
                payload={"text": "Backpropagation is the central mechanism by which neural networks learn.", "topic": "AI"}
            )
        ]
    )
    print("Sample data inserted.")

if __name__ == "__main__":
    setup_collections()

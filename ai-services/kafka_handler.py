import json
from kafka import KafkaProducer, KafkaConsumer
import threading

class KafkaHandler:
    def __init__(self, bootstrap_servers='localhost:9092'):
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        self.bootstrap_servers = bootstrap_servers

    def send_message(self, topic, message):
        self.producer.send(topic, message)
        self.producer.flush()

    def start_consumer(self, topic, callback):
        def consume():
            consumer = KafkaConsumer(
                topic,
                bootstrap_servers=self.bootstrap_servers,
                value_deserializer=lambda x: json.loads(x.decode('utf-8')),
                auto_offset_reset='earliest'
            )
            for message in consumer:
                callback(message.value)

        thread = threading.Thread(target=consume, daemon=True)
        thread.start()

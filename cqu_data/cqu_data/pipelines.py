# Define your item pipelines here
#
# Don't forget to add your pipeline to the ITEM_PIPELINES setting
# See: https://docs.scrapy.org/en/latest/topics/item-pipeline.html


# useful for handling different item types with a single interface
import uuid
import json
from datetime import datetime
from itemadapter import ItemAdapter
from scrapy.utils.serialize import ScrapyJSONEncoder
from pymongo import MongoClient
import pymongo

class CquDataPipeline:
    def open_spider(self, spider):
        self.file = open('cqu_data.json', 'w', encoding='utf-8')
        # self.file.write('[\n')

    def close_spider(self, spider):
        # self.file.write('\n]')
        self.file.close()

    def process_item(self, item, spider):
        line = json.dumps(dict(item), ensure_ascii=False) + ",\n"
        self.file.write(line)
        return item

class MongoDBPipeline:
    def __init__(self, mongo_uri, mongo_db, mongo_collection):
        self.mongo_uri = mongo_uri
        self.mongo_db = mongo_db
        self.mongo_collection = mongo_collection
        self.json_encoder = ScrapyJSONEncoder()
        self.counter = 0  # 可选：用于生成序列ID

    @classmethod
    def from_crawler(cls, crawler):
        return cls(
            mongo_uri=crawler.settings.get('MONGO_URI'),
            mongo_db=crawler.settings.get('MONGO_DATABASE'),
            mongo_collection=crawler.settings.get('MONGO_COLLECTION')
        )

    def open_spider(self, spider):
        self.client = MongoClient(self.mongo_uri)
        self.db = self.client[self.mongo_db]
        self.collection = self.db[self.mongo_collection]
        self.buffer = []  # 用于批量插入
        
        # self.collection.create_index("category", name="category_index")  
        # self.collection.create_index("section", name="section_index") 

    def close_spider(self, spider):
        if self.buffer:  # 确保最后一批数据被写入
            self._flush_buffer(spider)
        self.client.close()

    def process_item(self, item, spider):
        item_dict = ItemAdapter(item).asdict()
        
        # 生成唯一ID - 选择以下任意一种方法
        # 方法1: 使用UUID (推荐)
        item_dict['_id'] = str(uuid.uuid4())
        
        # 方法2: 使用时间戳+序列号
        # item_dict['_id'] = f"{datetime.now().strftime('%Y%m%d%H%M%S%f')}_{self.counter}"
        # self.counter += 1
        
        # 方法3: 使用内容哈希 (确保内容唯一)
        # import hashlib
        # content_hash = hashlib.sha256(item_dict['content'].encode('utf-8')).hexdigest()
        # item_dict['_id'] = f"{content_hash[:16]}"
        
        # 添加爬取时间戳
        item_dict['timestamp'] = datetime.now().isoformat()
        
        # 批量插入（提高性能）
        self.buffer.append(item_dict)
        if len(self.buffer) >= 100:  # 每100条写入一次
            self._flush_buffer(spider)
            
        return item

    def _flush_buffer(self,spider):
        """将缓冲区的数据批量写入MongoDB"""
        if self.buffer:
            try:
                # self.collection.insert_many(self.buffer, ordered=False)
                # 写入本地JSON文件（可选）
                with open("cqu_data.json", "a", encoding="utf-8") as f:
                    for item in self.buffer:
                        # f.write(self.json_encoder.encode(item) + "\n")
                        f.write(json.dumps(item, ensure_ascii=False) + "\n")
            except Exception as e:
                spider.logger.error(f"插入MongoDB出错: {e}")
            finally:
                self.buffer = []  # 清空缓冲区
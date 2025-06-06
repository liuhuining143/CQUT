# Define here the models for your scraped items
#
# See documentation in:
# https://docs.scrapy.org/en/latest/topics/items.html

import scrapy


class CquDataItem(scrapy.Item):
    # define the fields for your item here like:
    # name = scrapy.Field()
    category = scrapy.Field()        # 分类：历史、院系、师资等
    title = scrapy.Field()           # 标题
    content = scrapy.Field()         # 内容
    # source_url = scrapy.Field()      # 来源URL
    # timestamp = scrapy.Field()       # 爬取时间戳
    section = scrapy.Field()         # 子分类（如院系名称）

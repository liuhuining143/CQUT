import scrapy
from cqu_data.items import CquDataItem

class CquHistoryCelebritiesSpider(scrapy.Spider):
    name = "cqu_history_celebrities"
    allowed_domains = ["www.cqu.edu.cn"]
    start_urls = ["https://www.cqu.edu.cn/xqgk/lsmr.htm"]

    def parse(self, response):
        for celeb in response.css('ul.z-celebU1 > li'):
            item = CquDataItem()
            
            # 提取基本信息
            header = celeb.css('div.hd')
            item['title'] = header.css('span:nth-child(1)::text').get().strip()
            lifespan = header.css('span:nth-child(2)::text').get()
            position = header.css('span:nth-child(3)::text').get()
            
            # 提取描述文本
            description = celeb.css('div.cont div.text div.txt p::text').getall()
            description_text = ''.join(desc.strip() for desc in description if desc.strip())
            
            # 构建完整内容
            content_parts = []
            if lifespan and lifespan.strip() != '—':
                content_parts.append(f"生卒年：{lifespan.strip()}")
            if position:
                content_parts.append(f"身份：{position.strip()}")
            content_parts.append(f"描述：{description_text}")
            
            item['content'] = '\n'.join(content_parts)
            item['category'] = '学校历史'
            item['section'] = '历史名人'
            yield item


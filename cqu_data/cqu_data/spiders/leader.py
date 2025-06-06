import scrapy
from cqu_data.items import CquDataItem
import re
class LeaderSpider(scrapy.Spider):
    name = "leader"
    allowed_domains = ["www.cqu.edu.cn"]
    start_urls = ["https://www.cqu.edu.cn/xqgk/xrld.htm"]

    def parse(self, response):
        # 主分类信息
        category = "学校历史"
        section = "现任领导"

        # 第一部分：党委书记、副书记
        for li in response.css('div.row:first-child .jl-list2:first-child li'):
            job = ''.join(li.css('div.lab ::text').getall()).strip()
            name = li.css('div.name a::text').get()
            if name:
                # 清理姓名中的全角空格
                name = self.clean_name(name)
                yield self.create_item(category, name, job, section)

        # 第二部分：校长、副校长
        for li in response.css('div.row:first-child .jl-list2:nth-child(2) li:not([style*="display:none"])'):
            job = ''.join(li.css('div.lab ::text').getall()).strip()
            name = li.css('div.name a::text').get() or li.css('div.name i a::text').get()
            if name:
                name = self.clean_name(name)
                yield self.create_item(category, name, job, section)

        # 第三部分：党委常委和校长助理
        for li in response.css('div.row:nth-child(2) .jl-list2 li'):
            job = li.css('div.lab::text').get().replace("：", "").strip()
            names_text = ''.join(li.css('div.name ::text').getall()).strip()
            
            # 优化姓名分割逻辑
            names = self.process_names(names_text)
            for name in names:
                name = self.clean_name(name)
                yield self.create_item(category, name, job, section)

    def clean_name(self, name):
        """清理姓名中的特殊空格和空白字符"""
        # 替换全角空格(\u3000)和多个连续空格
        name = re.sub(r'[\u200b\u3000\s]+', '', name)
        return name.strip()

    def process_names(self, names_text):
        """处理党委常委部分的姓名分割"""
        names_text = re.sub(r'<[^>]+>', '', names_text)
        # 替换特殊空格和换行符
        names_text = re.sub(r'[\n\r\t\u3000]+', '', names_text)
        # 分割姓名（支持中文/英文字符的分割）
        names = re.split(r'[\s,，、]+', names_text)
        for i, name in enumerate(names):
            if name == "邓绍江卢义玉":
                # 替换当前元素为分割后的两个名字
                names[i:i+1] = ["邓绍江", "卢义玉"]
        # 过滤空姓名
        return [name for name in names if name.strip()]

    def create_item(self, category, title, content, section):
        item = CquDataItem()
        item['category'] = category
        item['title'] = content  # 职位作为title
        item['content'] = title  # 姓名作为content
        item['section'] = section
        return item
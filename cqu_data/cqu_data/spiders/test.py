import scrapy
from cqu_data.items import CquDataItem
from bs4 import BeautifulSoup

class TestSpider(scrapy.Spider):
    name = "test"
    allowed_domains = ["www.cqu.edu.cn"]
    start_urls = ["https://www.cqu.edu.cn/xqgk/xxjj.htm"]

    def parse(self, response):
        soup = BeautifulSoup(response.text, 'html.parser')
        info=response.xpath('//div[@class="info"]')
        massage_list=info.xpath('./p')
        for massage in massage_list:
            temp=CquDataItem()
            temp["category"]="学校历史"
            temp["title"]="校情概况"
            text = massage.xpath('text()').get()  # 或者使用 .extract_first()
            temp["content"]=text
            temp["section"]="学校简介"
            yield temp
        culture=response.xpath('//div[@class="m-culturels1"]')
        temp=CquDataItem()
        temp["category"]="学校历史"
        temp["section"]="学校简介"
        temp["title"]= culture.xpath('.//span[@class="t"]/text()').get()
        desc_paragraphs = culture.xpath('.//div[@class="desc"]/p/text()').getall()
        temp["content"] = "\n".join([p.strip() for p in desc_paragraphs if p.strip()])
        yield temp
        data = {
            "学科建设": self.extract_section(soup, ".ls-row1 .m-constructls1 .item:first-child .desc"),
            "人才队伍": self.extract_section(soup, ".ls-row1 .m-constructls1 .item:nth-child(2) .desc"),
            "科学研究": self.extract_section(soup, ".ls-row2 .box .ll .desc"),
            "国际合作": self.extract_section(soup, ".ls-row3 .m-internationall1 .rr .desc"),
            "校园文化": self.extract_section(soup, ".ls-row3 .m-campusl1 .desc"),
            "社会服务": self.extract_section(soup, ".ls-row3 .m-societyl1 .desc"),
        }
        # 清理文本数据
        for key in data:
            if data[key]:
               cleaned_content = self.clean_text(data[key])
        
            # 创建 CquDataItem 对象
            temp = CquDataItem()
            temp["category"] = "学校历史"  
            temp["section"] = "学校简介"  
            temp["title"] = key  
            temp["content"] = cleaned_content
            print(temp)
            # 返回 CquDataItem 对象
            yield temp
    
    def extract_section(self, soup, selector):
        """提取指定选择器的文本内容"""
        element = soup.select_one(selector)
        return element.get_text(strip=True, separator='\n') if element else None
    
    def clean_text(self, text):
        """清理文本数据"""
        # 替换特殊空格和换行符
        text = text.replace('\u3000', ' ').replace('\xa0', ' ')
        # 合并多余空格
        text = ' '.join(text.split())
        # 处理文本缩进
        text = text.replace('text-indent:2em;', '')
        return text.strip()


            
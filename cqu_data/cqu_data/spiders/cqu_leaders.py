import scrapy
from cqu_data.items import CquDataItem

class CquLeadersSpider(scrapy.Spider):
    name = "cqu_leaders"
    allowed_domains = ["www.cqu.edu.cn"]
    start_urls = ["https://www.cqu.edu.cn/xqgk/lrld.htm"]

    def parse(self, response):
        # 爬取历任党委书记信息
        for leader in response.css('div.z-leader1 > div.left .bd-main ul.z-txtU1 > li'):
            item = self.parse_leader(leader, "历任党委书记")
            if item:
                yield item
                
        # 爬取历任校长信息
        for leader in response.css('div.z-leader1 > div.right .bd-main ul.z-txtU1 > li'):
            item = self.parse_leader(leader, "历任校长")
            if item:
                yield item
                
    def parse_leader(self, leader, position_type):
        # 获取学校分类（重庆大学/原重庆建筑大学/原重庆建筑高等专科学校）
        school = leader.xpath('ancestor::div[@class="items"]/preceding-sibling::div[@class="bd-hd"]/div[@class="tit"]/text()').get()
        if not school:
            school = "重庆大学"  # 默认值
            
        # 提取职位标题
        title = leader.css('div.tit::text').get().strip()
        
        # 提取领导姓名
        name = leader.css('div.text h4.name::text').get().strip()
        
        # 提取内容描述
        content = "".join(leader.css('div.text div.txt p::text').getall()).strip()
        if not content:
            content = "".join(leader.css('div.text div.txt::text').getall()).strip()
        
        # 创建数据项
        item = CquDataItem()
        item['category'] = '学校历史'
        item['section'] = f"{position_type}-{school}"
        item['title'] = f"{title} - {name}"
        item['content'] = content
        
        return item


import scrapy
from cqu_data.items import CquDataItem

class CquDepartmentsSpider(scrapy.Spider):
    name = "cqu_departments2"
    allowed_domains = ["www.cqu.edu.cn"]
    start_urls = ["https://www.cqu.edu.cn/jgsz/glfwjg.htm"]

    def parse(self, response):
        # 遍历所有学部
        for section in response.css('ul.z-viewU2 > li'):
            section_name = section.css('.hd .tit a::text').get().strip()
            
            # 遍历学部下的所有学院
            for college in section.css('.text .link .item'):
                              
                # 提取下属系/研究所
                departments = []
                for dept in college.css('.item-sub1 .item-sub1-li'):
                    dept_name = dept.css('a span::text, a:not(span)::text').get()
                    if dept_name:
                        departments.append(dept_name.strip())
                
                # 提取学院名称 - 修正后的选择器
                # college_name = college.css('a > span::text').get()
                college_name = college.css('a[href^="http://"] span::text').get()
                if not college_name:
                    # college_name = college.css('a:not(span)::text').get()
                    college_name = college.css('a > span::text').get()
                
                if college_name:
                    college_name = college_name.rstrip('、').strip()
                else:
                    # 如果名称获取失败，使用备选方案
                    college_name = college.css('a::text').get() or ""
                    college_name = college_name.split('<')[0].strip().rstrip('、')
                
                # 创建Item对象
                item = CquDataItem()
                item['category'] = '院系概况'
                item['section'] = f"管理服务机构-{section_name}"
                item['title'] = college_name  # 确保这是学院名称
                item['content'] = ', '.join(departments) if departments else ''
                print(item)
                yield item
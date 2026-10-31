INSERT INTO demo_customer VALUES (1,'林知夏','13800138001','金卡',680.00,1280),(2,'陈思远','13800138002','银卡',230.00,520),(3,'王小雨','13800138003','普通会员',0,120);
INSERT INTO demo_account_binding(username,customer_id,employee_id,role_code,display_name,title) VALUES ('owner',1,NULL,'OWNER','林知夏','金卡会员'),('groomer',NULL,1,'GROOMER','周凯','首席美容师'),('manager',NULL,NULL,'MANAGER','林晓晴','旗舰店店长'),('reception',NULL,NULL,'RECEPTION','陈雨桐','前台接待');
INSERT INTO demo_pet VALUES
 (1,1,'糯米','DOG','比熊','SMALL',5.2,'卷毛','青年（1-3岁） · 无已知过敏 · 皮肤健康 · 曾处理严重打结','YOUNG','NONE','HEALTHY','MATTING'),
 (2,1,'旺财','DOG','金毛','LARGE',28.3,'双层毛','成年（3-7岁） · 香波香精过敏 · 过敏性皮炎史 · 曾做深层SPA','ADULT','FRAGRANCE','DERMATITIS','SPA'),
 (3,2,'雪球','CAT','布偶猫','SMALL',4.8,'长毛','青年（1-3岁） · 无已知过敏 · 皮肤健康 · 定期基础洗护','YOUNG','NONE','HEALTHY','BASIC'),
 (4,3,'棉花糖','DOG','萨摩耶','LARGE',26.0,'双层毛','成年（3-7岁） · 无已知过敏 · 皮肤健康 · 曾处理严重打结','ADULT','NONE','HEALTHY','MATTING');
INSERT INTO demo_service VALUES (1,'精致沐浴护理','基础洗护',98,60,'ALL',1),(2,'蓬松造型修剪','造型美容',168,90,'DOG',1),(3,'舒缓 SPA 护理','深层护理',148,75,'ALL',1),(4,'猫咪静音洗护','猫咪专护',128,75,'CAT',1);
INSERT INTO demo_employee VALUES (1,'周凯','首席美容师','比熊造型、精致修剪','DOG','SMALL,MEDIUM,LARGE'),(2,'张伟','资深洗护师','大型犬护理、SPA','DOG','SMALL,MEDIUM,LARGE,GIANT'),(3,'苏婉','猫咪护理师','猫咪低应激洗护','CAT','SMALL,MEDIUM');
INSERT INTO demo_resource VALUES (1,'1 号洗护区','DOG'),(2,'2 号洗护区','DOG'),(3,'猫咪静音室','CAT');

INSERT INTO demo_appointment VALUES
 (1,1,1,2,1,1,CURRENT_DATE,'09:30:00','11:00:00',168,'COMPLETED'),
 (2,1,2,3,2,2,CURRENT_DATE,'14:00:00','15:15:00',198,'IN_SERVICE'),
 (3,2,3,4,3,3,CURRENT_DATE,'15:00:00','16:15:00',128,'CONFIRMED'),
 (4,1,2,1,2,2,DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY),'11:00:00','12:00:00',148,'CONFIRMED');
INSERT INTO demo_grooming (id,pet_id,customer_id,employee_id,service_id,stage,status,pickup_code) VALUES
 (1,4,3,2,3,'BATHING','GROOMING','776201'),
 (2,1,1,1,2,'DONE','WAITING_PICKUP','882103'),
 (3,2,1,2,3,'DRYING','GROOMING','820451'),
 (4,1,1,1,1,'BATHING','GROOMING','661204');
INSERT INTO demo_addon VALUES (1,3,'低敏香波替换','旺财对香精敏感，建议本次改用燕麦低敏香波',30,'PENDING');
INSERT INTO demo_product (id,name,stock,threshold,unit,price) VALUES (1,'燕麦低敏香波',2300,800,'ml',68.00),(2,'碳酸泉 SPA 泡腾锭',18,30,'锭',42.00),(3,'柔白护毛精华',450,500,'ml',56.00);
INSERT INTO demo_card VALUES (1,1,'萌宠畅洗卡',6,10),(2,2,'猫咪护理卡',3,5);
INSERT INTO demo_card_service VALUES (1,1),(1,2),(1,3),(2,4);
INSERT INTO demo_review VALUES (1,'林知夏','糯米',5,'造型很满意，护理过程也非常细心。'),(2,'陈思远','雪球',5,'猫咪全程很安静，感谢耐心照顾。');
INSERT INTO demo_health_record (id,pet_id,grooming_id,employee_id,recorded_on,weight,skin_code,coat_code,ear_code,score) VALUES
 (1,1,NULL,1,DATE_ADD(CURRENT_DATE, INTERVAL -60 DAY),4.8,'CLEAR','LIGHT','CLEAN',93),
 (2,1,NULL,1,DATE_ADD(CURRENT_DATE, INTERVAL -30 DAY),5.0,'CLEAR','SMOOTH','CLEAN',100),
 (3,1,NULL,1,DATE_ADD(CURRENT_DATE, INTERVAL -7 DAY),5.2,'DRY','LIGHT','MILD',79),
 (4,2,NULL,2,DATE_ADD(CURRENT_DATE, INTERVAL -45 DAY),27.5,'DRY','MODERATE','MILD',72),
 (5,2,NULL,2,DATE_ADD(CURRENT_DATE, INTERVAL -15 DAY),28.0,'CLEAR','LIGHT','CLEAN',93),
 (6,3,NULL,3,DATE_ADD(CURRENT_DATE, INTERVAL -20 DAY),4.8,'CLEAR','SMOOTH','CLEAN',100);
INSERT INTO demo_order (id,customer_id,product_id,product_name,quantity,unit_price,amount,status,created_at) VALUES
 (1,1,1,'燕麦低敏香波',1,68.00,68.00,'SUBMITTED',DATE_ADD(CURRENT_TIMESTAMP, INTERVAL -2 DAY));

ALTER TABLE demo_appointment AUTO_INCREMENT = 5;
ALTER TABLE demo_grooming AUTO_INCREMENT = 5;
ALTER TABLE demo_addon AUTO_INCREMENT = 2;
ALTER TABLE demo_health_record AUTO_INCREMENT = 7;
ALTER TABLE demo_order AUTO_INCREMENT = 2;

CREATE TABLE demo_employee_schedule (employee_id BIGINT NOT NULL, schedule_date DATE NOT NULL, status VARCHAR(10) NOT NULL, start_time TIME, end_time TIME, PRIMARY KEY(employee_id,schedule_date));

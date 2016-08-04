package com.ly.qs.services.imp;

import com.quzheng.service.dao.mapper.QzUrlMapper;
import com.quzheng.service.dao.model.QzUrl;
import com.quzheng.service.dao.model.TSpAuth;
import com.quzheng.service.impl.RunLog;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;


public class QuartzJobFactory implements Job {

	@Autowired
	QzUrlMapper qzUrlMapper;
	@Autowired
	SpecialService specialService;

	public void execute(JobExecutionContext context) throws JobExecutionException {
		TSpAuth scheduleJob;
		try {

			scheduleJob = (TSpAuth) context.getMergedJobDataMap().get("scheduleJob");

			QzUrl qzUrl = new QzUrl();
			qzUrl.setUrl_addr(scheduleJob.getAuthUrl());
			qzUrl.setUrl_name(scheduleJob.getAuthName());

			qzUrl.setUrl_id(String.valueOf(System.currentTimeMillis()+1));

			qzUrl.setDo_user_id(scheduleJob.getUserId());
			qzUrl.setPpassport_id(scheduleJob.getPasswordId());
			DateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

			qzUrl.setDo_user_time(format.format(new Date()));
			if (qzUrlMapper.insert(qzUrl)>0) {
				RunLog.logDao.info(scheduleJob.getAuthName() + "---------任务执行成功");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}

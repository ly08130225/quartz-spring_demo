package com.ly.qs.services;

import com.quzheng.service.dao.model.TSpAuth;
import com.quzheng.service.dao.model.TSpAuthExample;
import com.system.Config;
import org.apache.log4j.Logger;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 
 * @Description: 计划任务管理
 */
@Service
public class JobTaskService {
	public final Logger log = Logger.getLogger(this.getClass());
	@Autowired
	private Scheduler scheduler;

	@Autowired
	private SpecialService specialService;

	/**
	 * 从数据库中取 区别于getAllJob
	 * 
	 * @return
	 */
	public List<TSpAuth> getAllTask() {
		TSpAuthExample example = new TSpAuthExample();
		return specialService.selectByExample(example);
	}

	/**
	 * 添加到数据库中 区别于addJob
	 */
	public void addTask(TSpAuth job) {
		DateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		job.setCreateTime(format.format(new Date()));
		specialService.insertSpecial(job);
	}

	/**
	 * 从数据库中查询job
	 */
	public TSpAuth getTaskById(Long jobId) {
		TSpAuthExample example = new TSpAuthExample();
		example.createCriteria().andIdEqualTo(jobId);
		return specialService.selectByExample(example).get(0);
	}

	/**
	 * 更改任务状态
	 * 
	 * @throws SchedulerException
	 */
	public void changeStatus(Long jobId, String cmd) throws SchedulerException {
		TSpAuth job = getTaskById(jobId);
		if (job == null) {
			return;
		}
		if ("stop".equals(cmd)) {
			deleteJob(job);
			job.setStatus(Config.STATUS_NOT_RUNNING);
		} else if ("start".equals(cmd)) {
			job.setStatus(Config.STATUS_RUNNING);
			addJob(job);
		}
		specialService.updateSpecial(job);
	}

	/**
	 * 更改任务 cron表达式
	 * 
	 * @throws SchedulerException
	 */
	public void updateCron(Long jobId, String cron) throws SchedulerException {
		TSpAuth job = getTaskById(jobId);
		if (job == null) {
			return;
		}
		job.setTimeExp(cron);
		if (Config.STATUS_RUNNING.equals(job.getStatus())) {
			updateJobCron(job);
		}
		specialService.updateSpecial(job);

	}

	/**
	 * 添加任务
	 * 
	 * @param job
	 * @throws SchedulerException
	 */
	public void addJob(TSpAuth job){
//		if (job == null || !Config.STATUS_RUNNING.equals(job.getStatus())) {
//			return;
//		}

		log.debug(scheduler + ".......................................................................................add");
		TriggerKey triggerKey = TriggerKey.triggerKey(job.getAuthName(), job.getAuthUrl());

		CronTrigger trigger = null;
		try {
			trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
		} catch (SchedulerException e) {
			e.printStackTrace();
		}

		// 不存在，创建一个
		if (null == trigger) {
			Class clazz = QuartzJobFactory.class;

			JobDetail jobDetail = JobBuilder.newJob(clazz).withIdentity(job.getAuthName(), job.getAuthUrl()).build();

			jobDetail.getJobDataMap().put("scheduleJob", job);

			CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(job.getTimeExp());

			trigger = TriggerBuilder.newTrigger().withIdentity(job.getAuthName(), job.getAuthUrl()).withSchedule(scheduleBuilder).build();

			try {
				scheduler.scheduleJob(jobDetail, trigger);
			} catch (SchedulerException e) {
				e.printStackTrace();
			}

			System.out.println("定时器任务已添加---------------"+trigger.getCronExpression());
		} else {
			// Trigger已存在，那么更新相应的定时设置
			CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(job.getTimeExp());

			// 按新的cronExpression表达式重新构建trigger
			trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();

			// 按新的trigger重新设置job执行
			try {
				scheduler.rescheduleJob(triggerKey, trigger);
			} catch (SchedulerException e) {
				e.printStackTrace();
			}

			System.out.println("定时器任务已添加---------------"+trigger.getCronExpression());

		}
	}

	@PostConstruct
	public void init() throws Exception {

//		Scheduler scheduler = schedulerFactoryBean.getScheduler();

		// 这里获取任务信息数据
		TSpAuthExample example = new TSpAuthExample();
		List<TSpAuth> jobList = specialService.selectByExample(example);
	
		for (TSpAuth job : jobList) {
			addJob(job);
		}
	}

	/**
	 * 获取所有计划中的任务列表
	 * 
	 * @return
	 * @throws SchedulerException
	 */
	public List<TSpAuth> getAllJob() throws SchedulerException {
		GroupMatcher<JobKey> matcher = GroupMatcher.anyJobGroup();
		Set<JobKey> jobKeys = scheduler.getJobKeys(matcher);
		List<TSpAuth> jobList = new ArrayList<TSpAuth>();
		for (JobKey jobKey : jobKeys) {
			List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);
			for (Trigger trigger : triggers) {
				TSpAuth job = new TSpAuth();
				job.setAuthName(jobKey.getName());
				job.setAuthUrl(jobKey.getGroup());
				Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
				job.setStatus(triggerState.name());
				if (trigger instanceof CronTrigger) {
					CronTrigger cronTrigger = (CronTrigger) trigger;
					String cronExpression = cronTrigger.getCronExpression();
					job.setTimeExp(cronExpression);
				}
				jobList.add(job);
			}
		}
		return jobList;
	}

	/**
	 * 所有正在运行的job
	 * 
	 * @return
	 * @throws SchedulerException
	 */
	public List<TSpAuth> getRunningJob() throws SchedulerException {
		List<JobExecutionContext> executingJobs = scheduler.getCurrentlyExecutingJobs();
		List<TSpAuth> jobList = new ArrayList<TSpAuth>(executingJobs.size());
		for (JobExecutionContext executingJob : executingJobs) {
			TSpAuth job = new TSpAuth();
			JobDetail jobDetail = executingJob.getJobDetail();
			JobKey jobKey = jobDetail.getKey();
			Trigger trigger = executingJob.getTrigger();
			job.setAuthName(jobKey.getName());
			job.setAuthUrl(jobKey.getGroup());
			Trigger.TriggerState triggerState = scheduler.getTriggerState(trigger.getKey());
			job.setStatus(triggerState.name());
			if (trigger instanceof CronTrigger) {
				CronTrigger cronTrigger = (CronTrigger) trigger;
				String cronExpression = cronTrigger.getCronExpression();
				job.setTimeExp(cronExpression);
			}
			jobList.add(job);
		}
		return jobList;
	}

	/**
	 * 暂停一个job
	 * 
	 * @param scheduleJob
	 * @throws SchedulerException
	 */
	public void pauseJob(TSpAuth scheduleJob) throws SchedulerException {
		JobKey jobKey = JobKey.jobKey(scheduleJob.getAuthName(), scheduleJob.getAuthUrl());
		scheduler.pauseJob(jobKey);
	}

	/**
	 * 恢复一个job
	 * 
	 * @param scheduleJob
	 * @throws SchedulerException
	 */
	public void resumeJob(TSpAuth scheduleJob) throws SchedulerException {
		JobKey jobKey = JobKey.jobKey(scheduleJob.getAuthName(), scheduleJob.getAuthUrl());
		scheduler.resumeJob(jobKey);
	}

	/**
	 * 删除一个job
	 * 
	 * @param scheduleJob
	 * @throws SchedulerException
	 */
	public void deleteJob(TSpAuth scheduleJob) throws SchedulerException {
		JobKey jobKey = JobKey.jobKey(scheduleJob.getAuthName(), scheduleJob.getAuthUrl());
		scheduler.deleteJob(jobKey);

	}

	/**
	 * 立即执行job
	 * 
	 * @param scheduleJob
	 * @throws SchedulerException
	 */
	public void runAJobNow(TSpAuth scheduleJob) throws SchedulerException {
		JobKey jobKey = JobKey.jobKey(scheduleJob.getAuthName(), scheduleJob.getAuthUrl());
		scheduler.triggerJob(jobKey);
	}

	/**
	 * 更新job时间表达式
	 * 
	 * @param scheduleJob
	 * @throws SchedulerException
	 */
	public void updateJobCron(TSpAuth scheduleJob) throws SchedulerException {

		TriggerKey triggerKey = TriggerKey.triggerKey(scheduleJob.getAuthName(), scheduleJob.getAuthUrl());

		CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);

		CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getTimeExp());

		trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();

		scheduler.rescheduleJob(triggerKey, trigger);
	}

	public static void main(String[] args) throws Exception {
//		CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule("xxxxx");
		ApplicationContext context = new FileSystemXmlApplicationContext("");
		JobTaskService service = new JobTaskService();
		service.getRunningJob();
	}
}

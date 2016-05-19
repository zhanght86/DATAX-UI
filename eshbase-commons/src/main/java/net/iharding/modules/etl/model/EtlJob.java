package net.iharding.modules.etl.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import net.iharding.core.orm.IdEntity;

import org.guess.sys.model.User;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ETL调度作业Entity
 * @author Joe.zhang
 * @version 2016-01-30
 */
@Entity
@Table(name = "etl_job")
@JsonIgnoreProperties(value = { "tasks"})
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class EtlJob extends IdEntity {

	/**
	 * 调度作业名
	 */
	@Column(name="job_name")
	private String jobName;
	
	
	/**
	 * 状态
	 */
	private Integer status;
	/**
	 * ETL类型
	 */
	@Column(name="etl_type")
	private Integer etlType;
	/**
	 * 最后更新人
	 */
	@ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE },targetEntity = User.class,fetch = FetchType.LAZY)
	@JoinColumn(name="updateby_id")
	@NotFound(action = NotFoundAction.IGNORE)
	@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
	private User updater;
	/**
	 * 建立人
	 */
	@ManyToOne(cascade = { CascadeType.PERSIST, CascadeType.MERGE },targetEntity = User.class,fetch = FetchType.LAZY)
	@JoinColumn(name="createby_id")
	@NotFound(action = NotFoundAction.IGNORE)
	@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
	private User creater;
	/**
	 * 建立时间
	 */
	@Column(name="create_date")
	private Date createDate;
	/**
	 * 更新时间
	 */
	@Column(name="update_date")
	private Date updateDate;
	/**
	 * 启用标记
	 */
	@Column(name="check_label")
	private Integer checkLabel;
	/**
	 * 备注
	 */
	private String remark;
	
	@OneToMany(targetEntity=EtlTask.class,fetch = FetchType.LAZY,cascade=CascadeType.ALL,mappedBy="job")
	@OrderBy("id ASC")
	private Set<EtlTask> tasks;
	
	public String getJobName() {
		return jobName;
	}

	public void setJobName(String jobName) {
		this.jobName = jobName;
	}
	
	public Integer getEtlType() {
		return etlType;
	}

	public void setEtlType(Integer etlType) {
		this.etlType = etlType;
	}

	
	public Integer getStatus() {
		return status;
	}

	public void setStatus(Integer status) {
		this.status = status;
	}
	
	public User getUpdater() {
		return updater;
	}

	public void setUpdater(User updater) {
		this.updater = updater;
	}

	public User getCreater() {
		return creater;
	}

	public void setCreater(User creater) {
		this.creater = creater;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
	
	public Date getUpdateDate() {
		return updateDate;
	}

	public void setUpdateDate(Date updateDate) {
		this.updateDate = updateDate;
	}
	
	public Integer getCheckLabel() {
		return checkLabel;
	}

	public void setCheckLabel(Integer checkLabel) {
		this.checkLabel = checkLabel;
	}
	
	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public Set<EtlTask> getTasks() {
		return tasks;
	}

	public void setTasks(Set<EtlTask> tasks) {
		this.tasks = tasks;
	}
	
	public EtlTask getReaderTask(){
		for (EtlTask dpc : tasks) {
			if (dpc.getPlugin().getPluginType()==1){
				return dpc;
			}
		}
		return null;
	}
	
	/**
	 * 检查配置是否错误
	 * @return
	 */
	public boolean checkSetError(){
		
		return true;
	}
	
	/**
	 * 将ETL调度任务配置转换为字符串输出
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(300);
		sb.append(String.format("\njob:%s", this.getId()));
		sb.append("\nReader task conf:");
		sb.append(this.getReaderTask().toString());
		sb.append(String.format("\n\nWriter task [num %d]:", this.tasks.size()-1));
		for (EtlTask dpc : tasks) {
			if (dpc.getPlugin().getPluginType()==2){
				sb.append(dpc.toString());
			}
		}
		return sb.toString();
	}

	public List<EtlTask> getWriterTasks() {
		List<EtlTask> wtasks=new ArrayList<EtlTask>(); 
		for(EtlTask task:wtasks){
			if (task.getPlugin().getPluginType()==2){
				wtasks.add(task);
			}
		}
		return wtasks;
	}
	
}

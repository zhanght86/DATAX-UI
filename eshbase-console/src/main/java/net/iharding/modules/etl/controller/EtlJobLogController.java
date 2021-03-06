package net.iharding.modules.etl.controller;

import org.guess.core.web.BaseController;
import net.iharding.modules.etl.model.EtlJobLog;
import net.iharding.modules.etl.service.EtlJobLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
* 
* @ClassName: EtlJobLog
* @Description: EtlJobLogController
* @author Joe.zhang
* @date  2016-1-30 17:26:11
*
*/
@Controller
@RequestMapping("/etl/EtlJobLog")
public class EtlJobLogController extends BaseController<EtlJobLog>{

	{
		editView = "/etl/EtlJobLog/edit";
		listView = "/etl/EtlJobLog/list";
		showView = "/etl/EtlJobLog/show";
	}
	
	@Autowired
	private EtlJobLogService etlJobLogService;
}

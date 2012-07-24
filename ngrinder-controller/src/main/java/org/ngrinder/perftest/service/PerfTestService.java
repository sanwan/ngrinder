/*
 * Copyright (C) 2012 - 2012 NHN Corporation
 * All rights reserved.
 *
 * This file is part of The nGrinder software distribution. Refer to
 * the file LICENSE which is part of The nGrinder distribution for
 * licensing details. The nGrinder distribution is available on the
 * Internet at http://nhnopensource.org/ngrinder
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.ngrinder.perftest.service;

import static org.ngrinder.common.util.Preconditions.checkNotNull;
import static org.ngrinder.common.util.Preconditions.checkNotZero;
import static org.ngrinder.perftest.repository.PerfTestSpecification.createdBy;
import static org.ngrinder.perftest.repository.PerfTestSpecification.emptyPredicate;
import static org.ngrinder.perftest.repository.PerfTestSpecification.likeTestNameOrDescription;
import static org.ngrinder.perftest.repository.PerfTestSpecification.statusSetEqual;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.List;

import net.grinder.common.GrinderProperties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.ngrinder.infra.config.Config;
import org.ngrinder.model.Role;
import org.ngrinder.model.User;
import org.ngrinder.perftest.model.PerfTest;
import org.ngrinder.perftest.model.Status;
import org.ngrinder.perftest.repository.PerfTestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;

/**
 * Performance Test Service Class.
 * 
 * @author Mavlarn
 * @author JunHo Yoon
 * @since 3.0
 */
@Service
public class PerfTestService {

	private static final String DATA_FILE_EXTENSION = ".data";

	@Autowired
	private PerfTestRepository perfTestRepository;

	@Autowired
	private Config config;
	
	/**
	 * Get {@link PerfTest} list on the user.
	 * 
	 * @param user
	 *            user
	 * @param query
	 * @param isFinished
	 *            only find finished test
	 * @param pageable
	 *            paging info
	 * @return found {@link PerfTest} list
	 */
	public Page<PerfTest> getPerfTestList(User user, String query, boolean isFinished, Pageable pageable) {

		Specifications<PerfTest> spec = Specifications.where(emptyPredicate());

		// User can see only his own test
		if (user.getRole() == Role.USER) {
			spec.and(createdBy(user));
		}

		if (isFinished) {
			spec = spec.and(statusSetEqual(Status.FINISHED));
		}
		if (StringUtils.isNotBlank(query)) {
			spec = spec.and(likeTestNameOrDescription(query));
		}
		return perfTestRepository.findAll(spec, pageable);
	}

	/**
	 * Save {@link PerfTest}.
	 * 
	 * @param perfTest
	 *            {@link PerfTest} instance to be saved.
	 * @return Saved {@link PerfTest}
	 */
	@CacheEvict(value = { "perftest", "perftestlist" }, allEntries = true)
	public PerfTest savePerfTest(PerfTest perfTest) {
		checkNotNull(perfTest);
		// Merge if necessary
		if (perfTest.exist()) {
			PerfTest existingPerfTest = perfTestRepository.findOne(perfTest.getId());
			perfTest = existingPerfTest.merge(perfTest);
		}
		return perfTestRepository.save(perfTest);
	}

	/**
	 * Save performance test with given status.
	 * 
	 * This method is only used for changing {@link Status}
	 * 
	 * @param perfTest
	 *            {@link PerfTest} instance which will be saved.
	 * @param status
	 *            Status to be assigned
	 * @return saved {@link PerfTest}
	 */
	@CacheEvict(value = { "perftest", "perftestlist" }, allEntries = true)
	public PerfTest savePerfTest(PerfTest perfTest, Status status) {
		checkNotNull(perfTest);
		checkNotNull(perfTest.getId(), "perfTest with status should save Id");
		perfTest.setStatus(checkNotNull(status, "status should not be null"));
		return perfTestRepository.save(perfTest);
	}

	/**
	 * Get PerfTest by testId.
	 * 
	 * @param testId
	 *            PerfTest id
	 * @return found {@link PerfTest}, null otherwise
	 */
	@Cacheable(value = "perftest")
	public PerfTest getPerfTest(long testId) {
		return perfTestRepository.findOne(testId);
	}

	/**
	 * Get next runnable PerfTest.
	 * 
	 * @return found {@link PerfTest}, null otherwise
	 */
	@Cacheable(value = "perftest")
	public PerfTest getPerfTestCandiate() {
		Page<PerfTest> perfTest = perfTestRepository.findAllByStatusOrderByCreatedDateAsc(Status.READY,
				new PageRequest(0, 1));
		return (perfTest.getNumber() == 0) ? null : perfTest.getContent().get(0);
	}

	/**
	 * Get currently testing PerfTest.
	 * 
	 * @return found {@link PerfTest} list
	 */
	@Cacheable(value = "perftestlist")
	public List<PerfTest> getTestingPerfTest() {
		return perfTestRepository.findAllByStatusOrderByCreatedDateAsc(Status.TESTING);
	}

	/**
	 * Delete PerfTest by id.
	 * 
	 * Never use this method in runtime. This method is used only for testing.
	 * 
	 * @param id
	 *            {@link PerfTest} it
	 */
	@CacheEvict(value = { "perftest", "perftestlist" }, allEntries = true)
	public void deletePerfTest(long id) {
		perfTestRepository.delete(id);
	}

	/**
	 * Get PerfTest Directory in which the distributed file is stored.
	 * 
	 * @param perfTest
	 *            pefTest from which distribution dire.ctory calculated
	 * @return path on in files are saved.
	 */
	public File getPerfTestFilePath(PerfTest perfTest) {
		return config.getHome().getPerfTestDirectory(
				checkNotZero(perfTest.getId(), "perftest id should not be 0 or zero").toString());
	}

	public GrinderProperties getGrinderProperties(PerfTest perfTest) {
		// TODO: still its empty
		return null;
	}

	public File prepareDistribution(PerfTest perfTest) {
		// TODO: still its empty
		return null;
	}

	public List<String> getReportData(long testId, String dataType, int imgWidth) throws IOException {
		List<String> reportData = new ArrayList<String>();

		File reportFolder = config.getHome().getPerfTestDirectory(testId + File.separator + "report");

		int pointCount = imgWidth / 10;

		int lineNumber;

		File targetFile = null;
		targetFile = new File(reportFolder, dataType.toLowerCase() + DATA_FILE_EXTENSION);

		LineNumberReader lnr = null;
		try {
			lnr = new LineNumberReader(new InputStreamReader(new FileInputStream(targetFile)));
			lnr.skip(targetFile.length());
			lineNumber = lnr.getLineNumber() + 1;

		} finally {
			IOUtils.closeQuietly(lnr);
		}

		FileReader reader = null;
		BufferedReader br = null;
		try {
			reader = new FileReader(targetFile);
			br = new BufferedReader(reader);
			String data = null;
			int current = 0;
			int interval = lineNumber / pointCount;
			// TODO should get average data
			while ((data = br.readLine()) != null) {
				if (0 == current) {
					reportData.add(data);
				}
				if (++current >= interval) {
					current = 0;
				}
			}
		} finally {
			IOUtils.closeQuietly(reader);
			IOUtils.closeQuietly(br);
		}

		return reportData;
	}
}

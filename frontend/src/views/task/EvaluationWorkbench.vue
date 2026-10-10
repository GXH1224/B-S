<template>
  <div class="ees-page">
    <PageHeader
      title="评估工作台"
      desc="分步引导：选择项目与指标体系 → 选择数据快照 → 选择算法与参数 → 确认提交 → 执行跟踪"
    >
      <template #actions>
        <el-button :icon="'RefreshLeft'" @click="resetAll">重置</el-button>
        <el-button :icon="'List'" @click="goTaskList">任务列表</el-button>
      </template>
    </PageHeader>

    <!-- 步骤条 -->
    <div class="ees-card wb-steps-card">
      <el-steps :active="step" align-center finish-status="success">
        <el-step title="项目与指标体系" description="选择评估项目与指标体系版本" />
        <el-step title="数据快照" description="选择待评估的数据快照" />
        <el-step title="算法与参数" description="选择算法并配置参数" />
        <el-step title="确认提交" description="核对输入并提交任务" />
        <el-step title="执行跟踪" description="查看进度、状态与结果" />
      </el-steps>
    </div>

    <!-- ---------------- 步骤 1：项目与指标体系 ---------------- -->
    <div v-show="step === 0" class="ees-card">
      <el-form label-width="130px" class="wb-form">
        <el-form-item label="评估项目" required>
          <el-select
            v-model="projectId"
            filterable
            clearable
            placeholder="请选择评估项目"
            class="wb-select"
            :loading="projectLoading"
            @change="onProjectChange"
          >
            <el-option v-for="p in projectOptions" :key="p.id" :label="projectLabel(p)" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="指标体系版本" required>
          <el-select
            v-model="versionId"
            filterable
            clearable
            :placeholder="projectId ? '请选择指标体系版本' : '请先选择项目，再选择指标体系版本'"
            class="wb-select"
            :disabled="!projectId"
            :loading="versionLoading"
            @change="onVersionChange"
          >
            <el-option v-for="v in versionOptions" :key="v.id" :label="versionLabel(v)" :value="v.id" />
          </el-select>
          <span class="ees-hint wb-inline-hint">建议选择状态为「已发布」的版本</span>
        </el-form-item>
      </el-form>

      <div v-if="!projectLoading && !projectOptions.length" class="ees-empty">
        暂无可选项目，请先在「项目管理」中创建项目
      </div>

      <!-- 指标体系版本为空时必须解释原因：版本在导入时绑定项目，
           若当前项目下没有可用版本，下拉会是空的，而占位文字此前仍在提示"请先选择项目"，容易误解 -->
      <el-alert
        v-if="projectId && !versionLoading && !versionOptions.length"
        class="wb-alert"
        type="warning"
        show-icon
        :closable="false"
        title="该项目下暂无可选的指标体系版本"
        description="指标体系版本在导入时绑定项目。请到「指标体系 → 导入指标体系」导入，并把「关联项目」选为本项目；若希望某个版本对所有项目都可见，导入时把「关联项目」留空即可（公共指标体系）。"
      />

      <el-alert
        v-if="versionId && !versionPublished"
        class="wb-alert"
        type="warning"
        show-icon
        :closable="false"
        title="所选指标体系版本尚未发布"
        :description="`当前版本状态为「${versionStatusText}」，未发布版本可能仍在校验或调整中，建议发布后再提交评估任务。`"
      />

      <el-descriptions v-if="versionInfo" :column="4" border size="small" class="wb-desc">
        <el-descriptions-item label="版本编号">
          <span class="ees-mono">{{ versionInfo.versionCode || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="版本名称">{{ versionInfo.versionName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="指标数">{{ fmtNum(versionInfo.indicatorCount, 0) }}</el-descriptions-item>
        <el-descriptions-item label="叶子数">{{ fmtNum(versionInfo.leafCount, 0) }}</el-descriptions-item>
        <el-descriptions-item label="层级数">{{ fmtNum(versionInfo.maxLevel, 0) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="versionStatusType">{{ versionStatusText }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="叶子指标">
          <span class="ees-mono">{{ leafCodes.length }}</span> 项可用于 AHP
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">
          {{ versionInfo.publishedAt ? fmtDateTime(versionInfo.publishedAt) : '尚未发布' }}
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- ---------------- 步骤 2：数据快照 ---------------- -->
    <div v-show="step === 1" class="ees-card">
      <div class="wb-section-head">
        <div class="wb-section-title">选择数据快照</div>
        <div class="ees-hint">
          仅列出当前项目下最近 {{ SNAPSHOT_PAGE_SIZE }} 条快照，点击表格行即选中（单选）
        </div>
      </div>

      <el-table
        v-loading="snapshotLoading"
        :data="snapshotRows"
        highlight-current-row
        height="380"
        size="small"
        @current-change="onSnapshotChange"
      >
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="快照编号" min-width="160">
          <template #default="{ row }">
            <span class="ees-mono">{{ row.snapshotCode || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="绑定指标体系版本" min-width="180">
          <template #default="{ row }">
            <span class="ees-mono">{{ row.indicatorVersionCode || '-' }}</span>
            <el-tag
              v-if="versionId && row.indicatorVersionId !== versionId"
              size="small"
              type="danger"
              class="wb-inline-tag"
            >版本不一致</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="记录数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.recordCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="对象数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.objectCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="指标数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.indicatorCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="缺失" width="80" align="right">
          <template #default="{ row }">
            <span :class="{ 'wb-danger-text': Number(row.missingCount) > 0 }">
              {{ fmtNum(row.missingCount, 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="越界" width="80" align="right">
          <template #default="{ row }">
            <span :class="{ 'wb-warning-text': Number(row.outlierCount) > 0 }">
              {{ fmtNum(row.outlierCount, 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="生成时间" width="170">
          <template #default="{ row }">{{ fmtDateTime(row.generatedAt) }}</template>
        </el-table-column>
      </el-table>

      <div v-if="!snapshotLoading && !snapshotRows.length" class="ees-empty">
        当前项目下暂无可选数据快照，请先在「数据快照」中生成快照
      </div>

      <el-alert
        v-if="snapshotVersionMismatch"
        class="wb-alert"
        type="error"
        show-icon
        :closable="false"
        title="数据快照绑定的指标体系版本与所选版本不一致，请重新生成快照"
        :description="`快照绑定版本：${selectedSnapshot?.indicatorVersionCode || '-'}；当前所选版本：${versionInfo?.versionCode || '-'}。请改用匹配的快照，或在「数据快照」页面基于当前指标体系版本重新生成快照后重试。`"
      />

      <el-descriptions v-if="selectedSnapshot" :column="4" border size="small" class="wb-desc">
        <el-descriptions-item label="已选快照">
          <span class="ees-mono">{{ selectedSnapshot.snapshotCode || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="被评估对象数">
          {{ fmtNum(selectedSnapshot.objectCount, 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="指标数">
          {{ fmtNum(selectedSnapshot.indicatorCount, 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="缺失 / 越界">
          {{ fmtNum(selectedSnapshot.missingCount, 0) }} / {{ fmtNum(selectedSnapshot.outlierCount, 0) }}
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- ---------------- 步骤 3：算法与参数 ---------------- -->
    <div v-show="step === 2" class="ees-card">
      <div class="wb-section-head">
        <div class="wb-section-title">选择评估算法</div>
        <div class="ees-hint">四种算法均由后端本地算法引擎执行，选中后可在下方逐项配置参数</div>
      </div>

      <div class="algo-grid">
        <div
          v-for="a in ALGORITHM_OPTIONS"
          :key="a.value"
          class="algo-card"
          :class="{ 'is-active': algorithm === a.value }"
          @click="chooseAlgorithm(a.value)"
        >
          <div class="algo-head">
            <span class="algo-name">{{ a.label }}</span>
            <el-tag v-if="algorithm === a.value" size="small" type="primary">已选</el-tag>
          </div>
          <div class="algo-desc">{{ a.desc }}</div>
          <div class="algo-code ees-mono">{{ a.value }}</div>
        </div>
      </div>

      <!-- 加权平均法 -->
      <div v-if="algorithm === 'WEIGHTED'" class="wb-param-box">
        <div class="wb-param-title">加权平均法参数</div>
        <el-form label-width="130px" class="wb-form">
          <el-form-item label="等级名称">
            <el-input v-model="weightedGradeNames" class="wb-input" placeholder="优,良,中,差" />
            <div class="ees-hint">以逗号分隔，顺序需与等级阈值一一对应（默认 优,良,中,差）</div>
          </el-form-item>
          <el-form-item label="等级阈值">
            <el-input v-model="weightedThresholds" class="wb-input" placeholder="90,80,60,0" />
            <div class="ees-hint">
              阈值必须<strong>降序</strong>排列：综合得分大于等于某阈值即判定为该等级，
              最后一项须为 0 以覆盖全部得分区间（默认 90,80,60,0）
            </div>
          </el-form-item>
        </el-form>
        <div class="wb-param-preview">
          <el-tag
            v-for="(g, i) in weightedPairs"
            :key="`w-${i}`"
            size="small"
            class="wb-pair-tag"
            :type="i === 0 ? 'success' : i === weightedPairs.length - 1 ? 'danger' : 'info'"
          >{{ g.name }} ≥ {{ g.threshold === undefined ? '-' : fmtNum(g.threshold, 0) }}</el-tag>
          <span v-if="!weightedPairs.length" class="ees-hint">尚未配置有效的等级名称与阈值</span>
        </div>
      </div>

      <!-- 模糊综合评判 -->
      <div v-else-if="algorithm === 'FUZZY'" class="wb-param-box">
        <div class="wb-param-title">模糊综合评判参数</div>
        <el-form label-width="130px" class="wb-form">
          <el-form-item label="评语集名称">
            <el-input v-model="fuzzyGradeNames" class="wb-input" placeholder="优,良,中,差" />
            <div class="ees-hint">以逗号分隔，默认 优,良,中,差</div>
          </el-form-item>
          <el-form-item label="等级量化分数">
            <el-input v-model="fuzzyGradeScores" class="wb-input" placeholder="95,85,70,55" />
            <div class="ees-hint">与评语集名称数量必须一致，用于隶属度与综合得分量化（默认 95,85,70,55）</div>
          </el-form-item>
          <el-form-item label="合成算子">
            <el-select v-model="fuzzyOperator" class="wb-input">
              <el-option v-for="op in FUZZY_OPERATORS" :key="op.value" :label="op.label" :value="op.value">
                <div class="wb-op-option">
                  <span class="wb-op-label">{{ op.label }}</span>
                  <span class="ees-hint">{{ op.desc }}</span>
                </div>
              </el-option>
            </el-select>
          </el-form-item>
        </el-form>
        <el-alert
          v-if="fuzzyOperatorDesc"
          class="wb-alert"
          type="info"
          show-icon
          :closable="false"
          :title="`当前合成算子：${fuzzyOperatorLabel}`"
          :description="fuzzyOperatorDesc"
        />
      </div>

      <!-- AHP / COMBO 公共：指标编码 + 判断矩阵 -->
      <div v-if="isMatrixAlgorithm" class="wb-param-box">
        <div class="wb-param-title">
          {{ algorithm === 'COMBO' ? '组合流程与 AHP 判断矩阵' : '层次分析法参数' }}
        </div>

        <el-form v-if="algorithm === 'COMBO'" label-width="130px" class="wb-form">
          <el-form-item label="组合流程">
            <div class="wb-flow">
              <el-tag type="primary" effect="dark">阶段一：AHP</el-tag>
              <el-icon class="wb-flow-arrow"><Right /></el-icon>
              <el-select v-model="comboSecond" class="wb-flow-select">
                <el-option label="阶段二：加权平均法（WEIGHTED）" value="WEIGHTED" />
                <el-option label="阶段二：模糊综合评判（FUZZY）" value="FUZZY" />
              </el-select>
            </div>
            <div class="ees-hint">先由 AHP 计算层级权重，再驱动所选算法完成综合评分</div>
          </el-form-item>
        </el-form>

        <el-form label-width="130px" class="wb-form">
          <el-form-item label="指标编码列表">
            <el-input
              v-model="criteriaText"
              type="textarea"
              :rows="2"
              class="wb-input"
              placeholder="例如：A11,A12,A21"
              @input="criteriaEdited = true"
            />
            <div class="ees-hint">
              已自动填充所选版本的叶子指标编码（共 {{ leafCodes.length }} 项），可手动修改；
              数量即为判断矩阵阶数 n
            </div>
            <el-button link type="primary" class="wb-inline-btn" @click="fillCriteriaFromVersion">
              从指标体系版本重新填充（{{ leafCodes.length }} 项）
            </el-button>
          </el-form-item>
        </el-form>

        <div class="wb-matrix-head">
          <div>
            <div class="wb-param-title">判断矩阵（{{ matrixSize }} × {{ matrixSize }}）</div>
            <div class="ees-hint">
              对角线恒为 1，元素取值为正数；建议采用 1~9 及其倒数标度
              （1 同等重要、3 稍重要、5 明显重要、7 强烈重要、9 极端重要）
            </div>
          </div>
          <div class="wb-matrix-actions">
            <el-button size="small" @click="makeReciprocal">生成互反矩阵（对称取倒数）</el-button>
            <el-button size="small" @click="resetMatrixIdentity">重置为单位矩阵</el-button>
          </div>
        </div>

        <div v-if="matrixSize >= 2" class="ees-scroll-x wb-matrix-scroll">
          <div
            class="wb-matrix"
            :style="{ gridTemplateColumns: `repeat(${matrixSize + 1}, minmax(112px, 1fr))` }"
          >
            <div class="wb-matrix-corner">行 \ 列</div>
            <div v-for="(c, j) in criteriaList" :key="`h-${j}`" class="wb-matrix-headcell">
              <span class="ees-mono">{{ c }}</span>
            </div>
            <template v-for="(c, i) in criteriaList" :key="`r-${i}`">
              <div class="wb-matrix-rowhead">
                <span class="ees-mono">{{ c }}</span>
              </div>
              <div v-for="(_, j) in criteriaList" :key="`c-${i}-${j}`" class="wb-matrix-cell">
                <el-input-number
                  v-model="matrix[i][j]"
                  :min="0.0001"
                  :max="9999"
                  :precision="4"
                  :step="i === j ? 0 : 0.5"
                  :controls="false"
                  :disabled="i === j"
                  size="small"
                  class="wb-matrix-input"
                  @change="onMatrixCellChange(i, j, $event)"
                />
              </div>
            </template>
          </div>
        </div>
        <div v-else class="ees-empty">
          判断矩阵至少需要 2 个指标，请先选择指标体系版本，或手动填写指标编码列表
        </div>

        <div class="wb-matrix-foot">
          <el-tag :type="reciprocalOk ? 'success' : 'warning'" size="small">
            {{ reciprocalOk ? '互反性校验通过：a(i,j) × a(j,i) = 1' : '互反性未满足，建议点击「生成互反矩阵」' }}
          </el-tag>
          <span class="ees-hint">对角线元素锁定为 1，不可编辑</span>
          <span class="ees-hint">一致性阈值：CR &lt; {{ CONSISTENCY_THRESHOLD }} 视为通过</span>
        </div>

        <el-form label-width="130px" class="wb-form wb-force-row">
          <el-form-item label="强制计算">
            <el-switch v-model="allowForceOnCrFail" />
            <span class="ees-hint wb-inline-hint">
              对应 parameters.allowForceOnCrFail；关闭时若 CR ≥ {{ CONSISTENCY_THRESHOLD }}，
              任务将校验失败并提示修正判断矩阵
            </span>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- ---------------- 步骤 4：确认并提交 ---------------- -->
    <div v-show="step === 3" class="ees-card">
      <div class="wb-section-head">
        <div class="wb-section-title">确认并提交</div>
        <div class="ees-hint">提交后后端先做输入校验再异步执行，可在「执行跟踪」与「任务详情」查看全过程</div>
      </div>

      <el-descriptions :column="2" border size="small" class="wb-desc">
        <el-descriptions-item label="项目">{{ selectedProjectLabel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="指标体系版本">
          {{ versionInfo ? `${versionInfo.versionCode || '-'} ${versionInfo.versionName || ''}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="数据快照">
          <span class="ees-mono">{{ selectedSnapshot?.snapshotCode || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="被评估对象数">
          {{ fmtNum(selectedSnapshot?.objectCount, 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="评估算法">
          {{ ALGORITHM_LABEL[algorithm] || algorithm }}
          <span class="ees-hint">（{{ algorithm }}）</span>
        </el-descriptions-item>
        <el-descriptions-item label="算法版本">
          <span class="ees-hint">提交后由后端算法注册表确定并记录到任务</span>
        </el-descriptions-item>
      </el-descriptions>

      <div class="wb-param-title wb-param-json-title">算法参数（parameters）</div>
      <pre class="ees-pre wb-pre">{{ parametersJson }}</pre>

      <el-form label-width="130px" class="wb-form wb-submit-form">
        <el-form-item label="任务名称">
          <el-input v-model="taskName" class="wb-input" :placeholder="defaultTaskName" maxlength="200" />
          <div class="ees-hint">留空时后端自动命名为「算法名称 - 快照编号」，当前默认：{{ defaultTaskName }}</div>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="!canCreate"
        class="wb-alert"
        type="warning"
        show-icon
        :closable="false"
        title="当前账号缺少 task:create 权限，无法提交评估任务"
      />

      <div class="wb-submit-bar">
        <el-button @click="prevStep">上一步</el-button>
        <el-button
          type="primary"
          :icon="'Promotion'"
          :loading="submitting"
          :disabled="!canCreate"
          @click="submitTask"
        >提交评估任务</el-button>
      </div>
    </div>

    <!-- ---------------- 步骤 5：执行跟踪 ---------------- -->
    <div v-if="step === 4" class="ees-card">
      <div class="wb-track-head">
        <div>
          <div class="wb-track-title">任务 {{ statusInfo?.taskCode || taskCode || '-' }}</div>
          <div class="ees-hint">
            提交后每 1.5 秒自动刷新一次状态，任务进入终态（成功 / 失败 / 已取消 / 已超时）后自动停止轮询
          </div>
        </div>
        <el-tag :type="statusTagType" effect="dark" size="large">{{ statusLabel }}</el-tag>
      </div>

      <el-progress
        class="wb-progress"
        :percentage="progressPercent"
        :color="progressColor"
        :stroke-width="16"
        :text-inside="true"
        striped
      />

      <el-descriptions :column="3" border size="small" class="wb-desc">
        <el-descriptions-item label="当前步骤">{{ statusInfo?.currentStep || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务状态">{{ statusLabel }}</el-descriptions-item>
        <el-descriptions-item label="结果数">{{ fmtNum(statusInfo?.resultCount, 0) }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ fmtDuration(statusInfo?.durationMs) }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ fmtDateTime(statusInfo?.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ fmtDateTime(statusInfo?.finishedAt) }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="taskStatus === 'SUCCESS'">
        <el-alert
          class="wb-alert"
          type="success"
          show-icon
          :closable="false"
          title="评估执行完成"
          :description="`共生成 ${fmtNum(statusInfo?.resultCount, 0)} 条对象结果，可前往结果分析查看文本、表格与图形三类展示。`"
        />
        <div class="wb-submit-bar">
          <el-button type="primary" :icon="'DataAnalysis'" @click="goResults">查看结果</el-button>
          <el-button :icon="'Document'" @click="goTaskDetail">查看任务</el-button>
          <el-button :icon="'Plus'" @click="startNewTask">再建一个任务</el-button>
        </div>
      </template>

      <template v-else-if="isTerminalStatus(taskStatus)">
        <el-alert
          class="wb-alert"
          type="error"
          show-icon
          :closable="false"
          :title="`任务未成功完成：${statusLabel}`"
          :description="errorDescription"
        />
        <div class="wb-submit-bar">
          <el-button type="primary" :icon="'RefreshRight'" :loading="retrying" @click="retryTask">重试</el-button>
          <el-button :icon="'Document'" @click="goTaskDetail">查看任务详情</el-button>
        </div>
      </template>

      <div v-else class="wb-tracking-hint">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>任务正在执行，状态将自动刷新…</span>
      </div>

      <div class="wb-track-foot ees-hint">
        <span v-if="pollFailures > 0">连续轮询失败 {{ pollFailures }} 次，正在自动重试</span>
        <span v-if="pollStoppedByFailure" class="wb-danger-text">
          状态轮询已因连续失败停止，请点击「刷新状态」手动查询
        </span>
      </div>

      <div class="wb-submit-bar">
        <el-button :icon="'Refresh'" @click="pollStatus">刷新状态</el-button>
        <el-button :icon="'List'" @click="goTaskList">返回任务列表</el-button>
      </div>
    </div>

    <!-- 步骤导航 -->
    <div v-if="step < 3" class="ees-card wb-nav">
      <el-button :disabled="step === 0" @click="prevStep">上一步</el-button>
      <el-button type="primary" @click="nextStep">下一步</el-button>
      <span v-if="currentIssue" class="ees-hint wb-nav-hint wb-danger-text">{{ currentIssue }}</span>
      <span v-else class="ees-hint wb-nav-hint">{{ currentStepHint }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { indicatorApi, projectApi, snapshotApi, taskApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import {
  ALGORITHM_LABEL,
  ALGORITHM_OPTIONS,
  TASK_STATUS_LABEL,
  TASK_STATUS_TYPE,
  VERSION_STATUS_LABEL,
  VERSION_STATUS_TYPE,
  fmtDateTime,
  fmtDuration,
  fmtNum
} from '@/utils/format'

defineOptions({ name: 'EvaluationWorkbench' })

/** ---------------- 常量 ---------------- */

const SNAPSHOT_PAGE_SIZE = 100
/** 一致性比率上限：CR < 0.1 视为通过（后端使用同一阈值） */
const CONSISTENCY_THRESHOLD = 0.1
const POLL_INTERVAL_MS = 1500
const MAX_POLL_FAILURES = 5
const TERMINAL_STATUS = ['SUCCESS', 'FAILED', 'CANCELED', 'TIMEOUT']

/** 模糊综合评判合成算子（取值与后端 FuzzyEngine 一致） */
const FUZZY_OPERATORS = [
  {
    value: 'M_PLUS',
    label: 'M(·,+) 加权平均型（默认）',
    desc: '先对隶属度加权再求和，保留全部指标信息，适用于多数综合评价场景'
  },
  {
    value: 'M_MIN_MAX',
    label: 'M(∧,∨) 主因素决定型',
    desc: '取最大隶属度决定等级，突出关键指标与短板，权重影响相对较小'
  },
  {
    value: 'M_PLUS_MAX',
    label: 'M(·,∨) 主因素突出型',
    desc: '先加权再取最大，兼顾权重与突出因素，适用于强调优势指标的对比'
  },
  {
    value: 'M_MIN_PLUS',
    label: 'M(∧,⊕) 加权求和型',
    desc: '先取小再求和，弱化单点极值影响，评判结果相对平缓'
  }
]

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const canCreate = computed(() => userStore.has('task:create'))
const canRetry = computed(() => userStore.has('task:retry'))

/** ---------------- 步骤 ---------------- */

const step = ref(0)

const STEP_HINTS = [
  '请选择评估项目与指标体系版本',
  '请选择与该版本绑定的数据快照',
  '请选择算法并完成参数配置',
  '请核对输入信息后提交',
  '任务执行中'
]

const currentStepHint = computed(() => STEP_HINTS[step.value] || '')

/** ---------------- 项目与指标体系 ---------------- */

const projectLoading = ref(false)
const projectOptions = ref<any[]>([])
const projectId = ref<number | undefined>(appStore.currentProjectId)

const versionLoading = ref(false)
const versionOptions = ref<any[]>([])
const versionId = ref<number | undefined>(undefined)
const versionDetail = ref<any | null>(null)
const leafCodes = ref<string[]>([])

function projectLabel(p: any): string {
  const name = p?.name || p?.projectName || '未命名项目'
  const code = p?.code || p?.projectCode
  return code ? `${name}（${code}）` : name
}

function versionLabel(v: any): string {
  const code = v?.versionCode || '未编号'
  const name = v?.versionName || ''
  const status = VERSION_STATUS_LABEL[String(v?.status)] || v?.status || ''
  return `${code}${name ? ` ${name}` : ''}${status ? ` · ${status}` : ''}`
}

const selectedProjectLabel = computed(() => {
  const hit = projectOptions.value.find((p) => Number(p?.id) === Number(projectId.value))
  return hit ? projectLabel(hit) : ''
})

const selectedVersionOption = computed(() =>
  versionOptions.value.find((v) => Number(v?.id) === Number(versionId.value))
)

/** 版本信息：优先取详情接口字段，缺失时回退到下拉项 */
const versionInfo = computed<any | null>(() => {
  if (!versionId.value) return null
  const opt = selectedVersionOption.value
  const detail = versionDetail.value
  if (!detail && !opt) return null
  return {
    versionCode: detail?.versionCode ?? opt?.versionCode,
    versionName: detail?.versionName ?? opt?.versionName,
    status: detail?.status ?? opt?.status,
    indicatorCount: detail?.indicatorCount ?? opt?.indicatorCount,
    leafCount: detail?.leafCount,
    maxLevel: detail?.maxLevel,
    publishedAt: detail?.publishedAt
  }
})

const versionStatusText = computed(
  () => VERSION_STATUS_LABEL[String(versionInfo.value?.status)] || versionInfo.value?.status || '-'
)
const versionStatusType = computed(() => VERSION_STATUS_TYPE[String(versionInfo.value?.status)] || 'info')
const versionPublished = computed(() => String(versionInfo.value?.status) === 'PUBLISHED')

async function loadProjects() {
  projectLoading.value = true
  try {
    const rows = await projectApi.options()
    projectOptions.value = Array.isArray(rows) ? rows : []
    if (projectId.value && !projectOptions.value.some((p) => Number(p?.id) === Number(projectId.value))) {
      projectId.value = undefined
      appStore.setCurrentProject(undefined)
    }
    if (projectId.value) await loadVersions()
  } catch {
    projectOptions.value = []
  } finally {
    projectLoading.value = false
  }
}

async function loadVersions() {
  if (!projectId.value) {
    versionOptions.value = []
    return
  }
  versionLoading.value = true
  try {
    const rows = await indicatorApi.options(projectId.value)
    versionOptions.value = Array.isArray(rows) ? rows : []
    if (versionId.value && !versionOptions.value.some((v) => Number(v?.id) === Number(versionId.value))) {
      versionId.value = undefined
      versionDetail.value = null
    }
  } catch {
    versionOptions.value = []
  } finally {
    versionLoading.value = false
  }
}

async function loadVersionDetail() {
  versionDetail.value = null
  if (!versionId.value) return
  try {
    versionDetail.value = await indicatorApi.get(versionId.value)
  } catch {
    versionDetail.value = null
  }
}

/** 读取叶子指标编码，作为 AHP 判断矩阵的默认指标列表 */
async function loadLeafCodes() {
  leafCodes.value = []
  if (!versionId.value) {
    if (!criteriaEdited.value) criteriaText.value = ''
    return
  }
  try {
    const rows = await indicatorApi.indicators(versionId.value)
    leafCodes.value = (Array.isArray(rows) ? rows : [])
      .filter((r) => r && (r.leaf === true || r.leaf === 'true'))
      .map((r) => String(r.indicatorCode ?? '').trim())
      .filter((code) => code.length > 0)
  } catch {
    leafCodes.value = []
  }
  if (!criteriaEdited.value) criteriaText.value = leafCodes.value.join(',')
}

async function onProjectChange() {
  appStore.setCurrentProject(projectId.value)
  versionId.value = undefined
  versionDetail.value = null
  versionOptions.value = []
  leafCodes.value = []
  snapshotRows.value = []
  selectedSnapshot.value = null
  snapshotId.value = undefined
  criteriaEdited.value = false
  criteriaText.value = ''
  if (projectId.value) await loadVersions()
}

async function onVersionChange() {
  criteriaEdited.value = false
  snapshotRows.value = []
  selectedSnapshot.value = null
  snapshotId.value = undefined
  await Promise.all([loadVersionDetail(), loadLeafCodes()])
}

/** ---------------- 数据快照 ---------------- */

const snapshotLoading = ref(false)
const snapshotRows = ref<any[]>([])
const snapshotId = ref<number | undefined>(undefined)
const selectedSnapshot = ref<any | null>(null)

const snapshotVersionMismatch = computed(() => {
  if (!selectedSnapshot.value || !versionId.value) return false
  return Number(selectedSnapshot.value.indicatorVersionId) !== Number(versionId.value)
})

async function loadSnapshots() {
  if (!projectId.value) {
    snapshotRows.value = []
    return
  }
  snapshotLoading.value = true
  try {
    const page: any = await snapshotApi.list({ projectId: projectId.value, size: SNAPSHOT_PAGE_SIZE })
    snapshotRows.value = Array.isArray(page?.records) ? page.records : []
    if (snapshotId.value) {
      const keep = snapshotRows.value.find((r) => Number(r?.id) === Number(snapshotId.value))
      if (keep) {
        selectedSnapshot.value = keep
      } else {
        snapshotId.value = undefined
        selectedSnapshot.value = null
      }
    }
  } catch {
    snapshotRows.value = []
  } finally {
    snapshotLoading.value = false
  }
}

function onSnapshotChange(row: any) {
  if (!row) {
    selectedSnapshot.value = null
    snapshotId.value = undefined
    return
  }
  selectedSnapshot.value = row
  snapshotId.value = Number(row.id)
  if (snapshotVersionMismatch.value) {
    ElMessage.warning('数据快照绑定的指标体系版本与所选版本不一致，请重新生成快照')
  }
}

/** ---------------- 算法与参数 ---------------- */

const algorithm = ref<string>('WEIGHTED')

const weightedGradeNames = ref('优,良,中,差')
const weightedThresholds = ref('90,80,60,0')

const fuzzyGradeNames = ref('优,良,中,差')
const fuzzyGradeScores = ref('95,85,70,55')
const fuzzyOperator = ref('M_PLUS')

const criteriaText = ref('')
const criteriaEdited = ref(false)
const allowForceOnCrFail = ref(false)
const comboSecond = ref<'WEIGHTED' | 'FUZZY'>('WEIGHTED')
const matrix = ref<number[][]>([[1]])

const isMatrixAlgorithm = computed(() => algorithm.value === 'AHP' || algorithm.value === 'COMBO')

/** 逗号 / 分号 / 顿号 / 空白分隔的文本 → 去空字符串数组 */
function splitList(text: string): string[] {
  return String(text ?? '')
    .split(/[,，;；、\s]+/)
    .map((s) => s.trim())
    .filter((s) => s.length > 0)
}

/** 逗号分隔文本 → 数字数组（忽略非法项） */
function splitNumbers(text: string): number[] {
  return splitList(text)
    .map((s) => Number(s))
    .filter((n) => Number.isFinite(n))
}

const weightedPairNames = computed(() => splitList(weightedGradeNames.value))
const weightedPairThresholds = computed(() => splitNumbers(weightedThresholds.value))

const weightedPairs = computed(() =>
  weightedPairNames.value.map((name, i) => ({
    name,
    threshold: weightedPairThresholds.value[i] as number | undefined
  }))
)

const fuzzyOperatorItem = computed(() => FUZZY_OPERATORS.find((o) => o.value === fuzzyOperator.value))
const fuzzyOperatorLabel = computed(() => fuzzyOperatorItem.value?.label || fuzzyOperator.value)
const fuzzyOperatorDesc = computed(() => fuzzyOperatorItem.value?.desc || '')

const criteriaList = computed(() => splitList(criteriaText.value))
const matrixSize = computed(() => criteriaList.value.length)

/** 互反性：a(i,j) × a(j,i) = 1 */
const reciprocalOk = computed(() => {
  const n = matrixSize.value
  if (n < 2 || matrix.value.length !== n) return false
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      const a = Number(matrix.value[i]?.[j])
      const b = Number(matrix.value[j]?.[i])
      if (!Number.isFinite(a) || !Number.isFinite(b) || a <= 0 || b <= 0) return false
      if (Math.abs(a * b - 1) > 1e-3) return false
    }
  }
  return true
})

/** 指标数量变化时同步矩阵阶数：保留既有取值并把对角线锁定为 1 */
function syncMatrix() {
  const n = matrixSize.value
  const old = matrix.value
  if (n < 1) {
    matrix.value = [[1]]
    return
  }
  const next: number[][] = []
  for (let i = 0; i < n; i++) {
    const row: number[] = []
    for (let j = 0; j < n; j++) {
      if (i === j) {
        row.push(1)
        continue
      }
      const prev = Number(old?.[i]?.[j])
      row.push(Number.isFinite(prev) && prev > 0 ? prev : 1)
    }
    next.push(row)
  }
  matrix.value = next
}

watch(criteriaList, () => syncMatrix(), { immediate: true })

function chooseAlgorithm(value: string) {
  algorithm.value = value
  if ((value === 'AHP' || value === 'COMBO') && !criteriaList.value.length && leafCodes.value.length) {
    criteriaEdited.value = false
    criteriaText.value = leafCodes.value.join(',')
  }
}

function fillCriteriaFromVersion() {
  if (!leafCodes.value.length) {
    ElMessage.warning('所选指标体系版本没有可用的叶子指标，请先完成指标体系导入与发布')
    return
  }
  criteriaEdited.value = false
  criteriaText.value = leafCodes.value.join(',')
  ElMessage.success(`已填充 ${leafCodes.value.length} 个叶子指标编码`)
}

function onMatrixCellChange(i: number, j: number, value: unknown) {
  const n = Number(value)
  if (!Number.isFinite(n) || n <= 0) {
    const row = matrix.value[i]
    if (row) row[j] = 1
  }
}

/** 生成互反矩阵：下三角取上三角的倒数，对角线为 1 */
function makeReciprocal() {
  const n = matrixSize.value
  if (n < 2) {
    ElMessage.warning('判断矩阵至少需要 2 个指标')
    return
  }
  for (let i = 0; i < n; i++) {
    matrix.value[i][i] = 1
    for (let j = i + 1; j < n; j++) {
      const raw = Number(matrix.value[i]?.[j])
      const safe = Number.isFinite(raw) && raw > 0 ? raw : 1
      matrix.value[i][j] = Number(safe.toFixed(4))
      matrix.value[j][i] = Number((1 / safe).toFixed(4))
    }
  }
  ElMessage.success('已按互反性（对称取倒数）生成判断矩阵')
}

/** 重置为单位矩阵 */
function resetMatrixIdentity() {
  const n = Math.max(matrixSize.value, 1)
  const next: number[][] = []
  for (let i = 0; i < n; i++) {
    const row: number[] = []
    for (let j = 0; j < n; j++) row.push(1)
    next.push(row)
  }
  matrix.value = next
  ElMessage.info('判断矩阵已重置为单位矩阵（所有元素为 1）')
}

/** ---------------- 提交参数与任务名 ---------------- */

const parameters = computed<Record<string, any>>(() => {
  if (algorithm.value === 'WEIGHTED') {
    return {
      gradeNames: weightedPairNames.value,
      gradeThresholds: weightedPairThresholds.value
    }
  }
  if (algorithm.value === 'FUZZY') {
    return {
      gradeNames: splitList(fuzzyGradeNames.value),
      gradeScores: splitNumbers(fuzzyGradeScores.value),
      operator: fuzzyOperator.value
    }
  }
  if (algorithm.value === 'AHP') {
    return {
      criteria: criteriaList.value,
      matrix: matrix.value.map((row) => row.slice()),
      allowForceOnCrFail: allowForceOnCrFail.value
    }
  }
  return {
    comboFlow: ['AHP', comboSecond.value],
    criteria: criteriaList.value,
    matrix: matrix.value.map((row) => row.slice()),
    allowForceOnCrFail: allowForceOnCrFail.value
  }
})

const parametersJson = computed(() => JSON.stringify(parameters.value, null, 2))

const defaultTaskName = computed(() => {
  const label = ALGORITHM_LABEL[algorithm.value] || algorithm.value
  const code = selectedSnapshot.value?.snapshotCode || ''
  return code ? `${label}评估 - ${code}` : `${label}评估`
})

const taskName = ref('')
const taskNameTouched = ref(false)

watch(taskName, (v) => {
  taskNameTouched.value = String(v ?? '').trim().length > 0 && v !== defaultTaskName.value
})

watch(defaultTaskName, (v) => {
  if (!taskNameTouched.value) taskName.value = v
})

/** ---------------- 分步校验 ---------------- */

function stepIssue(index: number): string {
  if (index === 0) {
    if (!projectId.value) return '请先选择评估项目'
    if (!versionId.value) return '请先选择指标体系版本'
    return ''
  }
  if (index === 1) {
    if (!snapshotId.value) return '请先选择数据快照'
    if (snapshotVersionMismatch.value) {
      return '数据快照绑定的指标体系版本与所选版本不一致，请重新生成快照'
    }
    if (Number(selectedSnapshot.value?.objectCount) <= 0) {
      return '所选快照中没有被评估对象，请重新生成快照'
    }
    return ''
  }
  if (index === 2) {
    if (algorithm.value === 'WEIGHTED') {
      const names = weightedPairNames.value
      const thresholds = weightedPairThresholds.value
      if (!names.length || !thresholds.length) return '请填写等级名称与等级阈值'
      if (names.length !== thresholds.length) {
        return `等级名称数量（${names.length}）与等级阈值数量（${thresholds.length}）不一致`
      }
      for (let i = 1; i < thresholds.length; i++) {
        if (thresholds[i] > thresholds[i - 1]) return '等级阈值必须按降序排列（例如 90,80,60,0）'
      }
      if (thresholds[thresholds.length - 1] !== 0) return '等级阈值最后一项必须为 0，以覆盖全部得分区间'
      return ''
    }
    if (algorithm.value === 'FUZZY') {
      const names = splitList(fuzzyGradeNames.value)
      const scores = splitNumbers(fuzzyGradeScores.value)
      if (!names.length) return '请填写评语集名称'
      if (!scores.length) return '请填写等级量化分数'
      if (names.length !== scores.length) {
        return `评语集名称数量（${names.length}）与等级量化分数数量（${scores.length}）不一致`
      }
      if (!fuzzyOperator.value) return '请选择合成算子'
      return ''
    }
    const n = matrixSize.value
    if (n < 2) return 'AHP 至少需要 2 个指标，请先选择指标体系版本或填写指标编码列表'
    if (matrix.value.length !== n) return '判断矩阵阶数与指标数量不一致，请重新填写指标编码列表'
    for (let i = 0; i < n; i++) {
      for (let j = 0; j < n; j++) {
        const v = Number(matrix.value[i]?.[j])
        if (!Number.isFinite(v) || v <= 0) {
          return `判断矩阵第 ${i + 1} 行第 ${j + 1} 列取值非法，必须为正数`
        }
      }
    }
    if (!reciprocalOk.value) return '判断矩阵不满足互反性，请先点击「生成互反矩阵」'
    if (algorithm.value === 'COMBO' && !comboSecond.value) return '请选择组合流程的阶段二算法'
    return ''
  }
  return ''
}

const currentIssue = computed(() => stepIssue(step.value))

function prevStep() {
  if (step.value <= 0) return
  step.value -= 1
  if (step.value === 1 && !snapshotRows.value.length) void loadSnapshots()
}

async function nextStep() {
  const issue = stepIssue(step.value)
  if (issue) {
    ElMessage.warning(issue)
    return
  }
  if (step.value === 0) {
    step.value = 1
    await loadSnapshots()
    return
  }
  if (step.value === 1) {
    await Promise.all([loadVersionDetail(), loadLeafCodes()])
    step.value = 2
    return
  }
  if (step.value === 2) {
    step.value = 3
    if (!String(taskName.value || '').trim()) taskName.value = defaultTaskName.value
  }
}

/** ---------------- 提交与执行跟踪 ---------------- */

const submitting = ref(false)
const taskId = ref<number | undefined>(undefined)
const taskCode = ref('')

const statusInfo = ref<any | null>(null)
const pollFailures = ref(0)
const pollStoppedByFailure = ref(false)
const retrying = ref(false)

let timer: number | undefined

function isTerminalStatus(status: unknown): boolean {
  return TERMINAL_STATUS.includes(String(status))
}

const taskStatus = computed(() => String(statusInfo.value?.status || 'QUEUED'))
const statusLabel = computed(() => TASK_STATUS_LABEL[taskStatus.value] || taskStatus.value || '-')
const statusTagType = computed(() => TASK_STATUS_TYPE[taskStatus.value] || 'info')

const progressPercent = computed(() => {
  const n = Number(statusInfo.value?.progress)
  if (!Number.isFinite(n)) return 0
  return Math.max(0, Math.min(100, Math.round(n)))
})

const progressColor = computed(() => {
  if (taskStatus.value === 'SUCCESS' || taskStatus.value === 'PUBLISHED') return 'var(--ees-success)'
  if (taskStatus.value === 'FAILED' || taskStatus.value === 'TIMEOUT') return 'var(--ees-danger)'
  if (taskStatus.value === 'CANCELED') return 'var(--ees-info)'
  return 'var(--ees-primary)'
})

const errorDescription = computed(() => {
  const code = statusInfo.value?.errorCode
  const msg = statusInfo.value?.errorMsg
  if (!code && !msg) return '后端未返回具体失败原因，可在任务详情中查看状态历史与错误信息'
  return `${code ? `错误码 ${code}：` : ''}${msg || '无详细描述'}`
})

function startPolling() {
  stopPolling()
  timer = window.setInterval(() => {
    void pollStatus()
  }, POLL_INTERVAL_MS)
}

function stopPolling() {
  if (timer !== undefined) {
    window.clearInterval(timer)
    timer = undefined
  }
}

async function pollStatus() {
  if (!taskId.value) return
  try {
    const s: any = await taskApi.status(taskId.value)
    if (s) statusInfo.value = s
    pollFailures.value = 0
    pollStoppedByFailure.value = false
    if (isTerminalStatus(s?.status)) stopPolling()
  } catch {
    pollFailures.value += 1
    if (pollFailures.value >= MAX_POLL_FAILURES) {
      stopPolling()
      pollStoppedByFailure.value = true
      ElMessage.warning('状态轮询连续失败，已停止自动刷新')
    }
  }
}

async function submitTask() {
  for (const i of [0, 1, 2]) {
    const issue = stepIssue(i)
    if (issue) {
      step.value = i
      ElMessage.warning(issue)
      return
    }
  }
  if (!projectId.value || !snapshotId.value || !versionId.value) {
    ElMessage.warning('项目、指标体系版本与数据快照均不能为空')
    return
  }
  submitting.value = true
  try {
    const res: any = await taskApi.create({
      projectId: projectId.value,
      snapshotId: snapshotId.value,
      indicatorVersionId: versionId.value,
      algorithm: algorithm.value,
      taskName: String(taskName.value || '').trim() || defaultTaskName.value,
      parameters: parameters.value
    })
    const newId = Number(res?.taskId)
    if (!Number.isFinite(newId) || newId <= 0) {
      ElMessage.error('任务已提交但未返回任务编号，请前往任务列表查看')
      await router.push('/tasks')
      return
    }
    taskId.value = newId
    taskCode.value = String(res?.taskCode || '')
    pollFailures.value = 0
    pollStoppedByFailure.value = false
    statusInfo.value = {
      taskId: newId,
      taskCode: taskCode.value,
      status: String(res?.status || 'QUEUED'),
      progress: 0,
      currentStep: '已进入执行队列'
    }
    step.value = 4
    ElMessage.success('任务已提交，正在异步执行')
    await pollStatus()
    startPolling()
  } catch {
    // 请求拦截器已给出中文错误提示
  } finally {
    submitting.value = false
  }
}

async function retryTask() {
  if (!taskId.value) return
  if (!canRetry.value) {
    ElMessage.warning('当前账号缺少 task:retry 权限')
    return
  }
  retrying.value = true
  try {
    const res: any = await taskApi.retry(taskId.value)
    const newId = Number(res?.taskId)
    stopPolling()
    pollFailures.value = 0
    pollStoppedByFailure.value = false
    if (Number.isFinite(newId) && newId > 0) {
      taskId.value = newId
      taskCode.value = String(res?.taskCode || '')
      statusInfo.value = {
        taskId: newId,
        taskCode: taskCode.value,
        status: String(res?.status || 'QUEUED'),
        progress: 0,
        currentStep: '已进入执行队列'
      }
      await pollStatus()
      startPolling()
    }
    ElMessage.success(`已生成新的执行实例${res?.retryOf ? `（来源 ${res.retryOf}）` : ''}`)
  } catch {
    // 请求拦截器已提示
  } finally {
    retrying.value = false
  }
}

/** ---------------- 导航与重置 ---------------- */

function goResults() {
  if (!taskId.value) return
  router.push({ path: '/results', query: { taskId: String(taskId.value) } })
}

function goTaskDetail() {
  if (!taskId.value) return
  router.push(`/tasks/${taskId.value}`)
}

function goTaskList() {
  router.push('/tasks')
}

/** 保留已选条件，回到算法步骤重新提交 */
function startNewTask() {
  stopPolling()
  taskId.value = undefined
  taskCode.value = ''
  statusInfo.value = null
  taskName.value = ''
  taskNameTouched.value = false
  pollFailures.value = 0
  pollStoppedByFailure.value = false
  step.value = 2
}

async function resetAll() {
  try {
    await ElMessageBox.confirm(
      '将清空已选择的项目、指标体系版本、数据快照、算法参数与执行跟踪信息，是否继续？',
      '重置工作台',
      { type: 'warning', confirmButtonText: '确定重置', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  stopPolling()
  step.value = 0
  projectId.value = appStore.currentProjectId
  versionId.value = undefined
  versionDetail.value = null
  versionOptions.value = []
  leafCodes.value = []
  snapshotId.value = undefined
  selectedSnapshot.value = null
  snapshotRows.value = []
  algorithm.value = 'WEIGHTED'
  weightedGradeNames.value = '优,良,中,差'
  weightedThresholds.value = '90,80,60,0'
  fuzzyGradeNames.value = '优,良,中,差'
  fuzzyGradeScores.value = '95,85,70,55'
  fuzzyOperator.value = 'M_PLUS'
  criteriaText.value = ''
  criteriaEdited.value = false
  allowForceOnCrFail.value = false
  comboSecond.value = 'WEIGHTED'
  // 直接复位矩阵，避免复用 resetMatrixIdentity 时弹出「已重置」提示
  matrix.value = [[1]]
  taskName.value = ''
  taskNameTouched.value = false
  taskId.value = undefined
  taskCode.value = ''
  statusInfo.value = null
  pollFailures.value = 0
  pollStoppedByFailure.value = false
  if (projectId.value) await loadVersions()
  ElMessage.success('工作台已重置')
}

onUnmounted(() => {
  stopPolling()
})

void loadProjects()
</script>

<style scoped>
.wb-steps-card {
  padding: 18px 16px 10px;
}

.wb-form {
  max-width: 920px;
}

.wb-select,
.wb-input {
  width: 380px;
}

.wb-inline-hint {
  margin-left: 10px;
}

.wb-inline-btn {
  margin-top: 4px;
}

.wb-inline-tag {
  margin-left: 6px;
}

.wb-alert {
  margin: 12px 0;
}

.wb-desc {
  margin-top: 10px;
}

.wb-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.wb-section-title {
  font-size: 15px;
  font-weight: 600;
}

.algo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(255px, 1fr));
  gap: 12px;
}

.algo-card {
  border: 1px solid var(--ees-border);
  border-radius: var(--ees-radius-sm);
  padding: 12px 14px;
  cursor: pointer;
  background: #fbfdff;
  transition: all 0.15s ease;
}

.algo-card:hover {
  border-color: var(--ees-primary-light);
  box-shadow: var(--ees-shadow-hover);
}

.algo-card.is-active {
  border-color: var(--ees-primary);
  background: #f0f6ff;
  box-shadow: 0 0 0 1px var(--ees-primary) inset;
}

.algo-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.algo-name {
  font-size: 14px;
  font-weight: 600;
}

.algo-desc {
  margin-top: 6px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--ees-text-secondary);
}

.algo-code {
  margin-top: 8px;
  color: var(--ees-text-muted);
}

.wb-param-box {
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px dashed var(--ees-border);
  border-radius: var(--ees-radius-sm);
  background: #fcfdff;
}

.wb-param-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}

.wb-param-json-title {
  margin-top: 16px;
}

.wb-param-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.wb-pair-tag {
  font-variant-numeric: tabular-nums;
}

.wb-op-option {
  display: flex;
  flex-direction: column;
  line-height: 1.5;
  padding: 2px 0;
}

.wb-op-label {
  font-size: 13px;
}

.wb-flow {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.wb-flow-arrow {
  color: var(--ees-text-muted);
}

.wb-flow-select {
  width: 300px;
}

.wb-matrix-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.wb-matrix-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.wb-matrix-scroll {
  margin-top: 10px;
  padding-bottom: 4px;
}

.wb-matrix {
  display: grid;
  gap: 4px;
  min-width: 320px;
}

.wb-matrix-corner,
.wb-matrix-headcell,
.wb-matrix-rowhead {
  font-size: 12px;
  color: var(--ees-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px 4px;
  background: #f7f9fd;
  border-radius: 4px;
  min-height: 30px;
  text-align: center;
  word-break: break-all;
}

.wb-matrix-corner {
  background: transparent;
  color: var(--ees-text-muted);
}

.wb-matrix-input {
  width: 100%;
}

.wb-matrix-foot {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.wb-force-row {
  margin-top: 6px;
}

.wb-submit-form {
  margin-top: 12px;
}

.wb-pre {
  max-height: 320px;
}

.wb-submit-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.wb-track-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.wb-track-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.wb-progress {
  margin-bottom: 14px;
}

.wb-tracking-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ees-text-secondary);
  padding: 10px 0;
}

.wb-track-foot {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.wb-nav {
  display: flex;
  align-items: center;
  gap: 10px;
}

.wb-nav-hint {
  margin-left: 6px;
}

.wb-danger-text {
  color: var(--ees-danger);
}

.wb-warning-text {
  color: var(--ees-warning);
}
</style>

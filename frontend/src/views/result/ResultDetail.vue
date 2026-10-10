<template>
  <div v-loading="loading" class="ees-page">
    <PageHeader :title="headerTitle" :desc="headerDesc">
      <template #actions>
        <el-button @click="goBack">
          <el-icon><Back /></el-icon>
          返回列表
        </el-button>
        <el-button :loading="loading" @click="reloadAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-dropdown v-if="canExport" trigger="click" @command="onExportCommand">
          <el-button type="primary" :loading="exporting">
            导出报告<el-icon><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="EXCEL">导出 Excel</el-dropdown-item>
              <el-dropdown-item command="WORD">导出 Word</el-dropdown-item>
              <el-dropdown-item command="PDF">导出 PDF</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="printPage">
          <el-icon><Printer /></el-icon>
          打印 / 导出 PDF
        </el-button>
        <el-button v-if="canReview" @click="openReview">复核</el-button>
        <el-button v-if="canPublish" type="success" plain @click="publishResult">发布</el-button>
        <el-button v-if="canPublish" plain @click="archiveResult">归档</el-button>
      </template>
    </PageHeader>

    <div v-if="!result" class="ees-card">
      <div class="ees-empty">
        未获取到该评估结果，可能已被删除或无权访问。
        <el-button link type="primary" @click="reloadAll">重新加载</el-button>
      </div>
    </div>

    <template v-else>
      <div class="ees-hint page-note">
        提示：打印 / 导出 PDF 使用浏览器打印对话框（选择「另存为 PDF」）；图形标签页中的图形需渲染完成后才会完整输出。
      </div>

      <!-- 1. 关键指标 -->
      <div class="ees-stat-grid">
        <StatCard
          label="综合得分"
          :value="fmtScore(result.totalScore)"
          unit="分"
          variant="primary"
          foot="数值直接取自后端评估结果"
        />
        <div class="ees-stat">
          <div class="ees-stat-label">评判等级</div>
          <div class="ees-stat-value">
            <LevelTag :level="result.level" />
          </div>
          <div class="ees-stat-foot">由算法按评语等级判定</div>
        </div>
        <StatCard
          label="排名"
          :value="result.ranking ? `第 ${result.ranking} 名` : '未排名'"
          icon="Trophy"
          foot="同任务内按综合得分降序"
        />
        <StatCard
          label="算法"
          :value="ALGORITHM_LABEL[result.algorithm] || result.algorithm || '未知算法'"
          icon="Cpu"
          foot="方案 6.1 统一算法接口"
        />
        <StatCard
          label="算法版本"
          :value="result.algorithmVersion || '-'"
          icon="PriceTag"
          foot="算法与版本随结果一同留痕"
        />
        <StatCard
          label="指标数"
          :value="allDetails.length"
          unit="项"
          icon="Grid"
          foot="参与评估的指标明细条数"
        />
      </div>

      <!-- 2. 数据与算法告警 -->
      <el-alert
        v-if="warnings.length"
        class="warn-block"
        type="warning"
        :closable="false"
        show-icon
        title="数据与算法告警"
      >
        <ul class="warn-list">
          <li v-for="(w, i) in warnings" :key="`warn-${i}`">{{ w }}</li>
        </ul>
      </el-alert>

      <!-- 3. 三类展示形式 -->
      <div class="ees-card">
        <el-tabs v-model="activeTab">
          <!-- ============ 文本 ============ -->
          <el-tab-pane name="text">
            <template #label>
              <span class="tab-label"><el-icon><Document /></el-icon>文本</span>
            </template>

            <div class="conclusion-card">
              <div class="conclusion-title">综合结论</div>
              <p class="conclusion-text">{{ conclusionSummary }}</p>
              <div class="ees-hint">
                结论文本由后端根据评估结果生成；得分、等级与排名与表格、图形同源（方案 7.3 结果一致性）。
              </div>
            </div>

            <div class="text-grid">
              <div class="sub-card">
                <div class="sub-title">
                  薄弱指标
                  <span class="ees-hint">（贡献值最低，提升空间最大）</span>
                </div>
                <el-table :data="weakIndicators" border size="small" empty-text="后端未提供薄弱指标数据">
                  <el-table-column prop="indicatorCode" label="指标编码" width="130" show-overflow-tooltip>
                    <template #default="{ row }">
                      <span class="ees-mono">{{ row?.indicatorCode || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="indicatorName" label="指标名称" min-width="150" show-overflow-tooltip>
                    <template #default="{ row }">{{ row?.indicatorName || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="得分" width="90" align="right">
                    <template #default="{ row }">
                      <span class="ees-score">{{ fmtScore(row?.score) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="权重" width="90" align="right">
                    <template #default="{ row }">{{ fmtWeight(row?.weight) }}</template>
                  </el-table-column>
                  <el-table-column label="贡献值" width="100" align="right">
                    <template #default="{ row }">{{ fmtNum(row?.contribution, 4) }}</template>
                  </el-table-column>
                  <el-table-column label="等级" width="82" align="center">
                    <template #default="{ row }">
                      <LevelTag :level="row?.grade" />
                    </template>
                  </el-table-column>
                </el-table>
              </div>

              <div class="sub-card">
                <div class="sub-title">
                  优势指标
                  <span class="ees-hint">（贡献值最高）</span>
                </div>
                <el-table :data="strongIndicators" border size="small" empty-text="后端未提供优势指标数据">
                  <el-table-column prop="indicatorCode" label="指标编码" width="130" show-overflow-tooltip>
                    <template #default="{ row }">
                      <span class="ees-mono">{{ row?.indicatorCode || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="indicatorName" label="指标名称" min-width="150" show-overflow-tooltip>
                    <template #default="{ row }">{{ row?.indicatorName || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="得分" width="90" align="right">
                    <template #default="{ row }">
                      <span class="ees-score">{{ fmtScore(row?.score) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="权重" width="90" align="right">
                    <template #default="{ row }">{{ fmtWeight(row?.weight) }}</template>
                  </el-table-column>
                  <el-table-column label="贡献值" width="100" align="right">
                    <template #default="{ row }">{{ fmtNum(row?.contribution, 4) }}</template>
                  </el-table-column>
                  <el-table-column label="等级" width="82" align="center">
                    <template #default="{ row }">
                      <LevelTag :level="row?.grade" />
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>

            <div class="sub-card">
              <div class="sub-title">改进建议</div>
              <el-timeline v-if="improvements.length">
                <el-timeline-item
                  v-for="(item, i) in improvements"
                  :key="`imp-${i}`"
                  type="primary"
                  placement="top"
                  :timestamp="`${item?.indicatorName || ''} ${item?.indicatorCode ? '(' + item.indicatorCode + ')' : ''}`"
                >
                  <div class="improve-text">{{ item?.suggestion || '后端未提供建议文本' }}</div>
                  <div class="ees-hint">
                    得分 {{ fmtScore(item?.score) }} · 权重 {{ fmtWeight(item?.weight) }}
                  </div>
                </el-timeline-item>
              </el-timeline>
              <div v-else class="ees-empty">暂无改进建议（所有指标得分均不低于 80 分）</div>
            </div>

            <!-- 模糊综合评判：完整评判向量 -->
            <div v-if="isFuzzy" class="sub-card">
              <div class="sub-title">综合评判向量（隶属度）</div>
              <div class="ees-hint">
                模糊综合评判保存的是完整综合评判向量（而非仅最大值），各分量之和为 1；判定等级取最大隶属度对应的评语。
                <span v-if="!gradeNames.length">后端未返回评语名称，已按序号展示。</span>
              </div>
              <el-table :data="vectorRows" border size="small" empty-text="后端未保存综合评判向量">
                <el-table-column prop="grade" label="评语" width="130">
                  <template #default="{ row }">{{ row?.grade || '-' }}</template>
                </el-table-column>
                <el-table-column label="隶属度" width="140" align="right">
                  <template #default="{ row }">
                    <span class="ees-mono">{{ fmtNum(row?.value, 6) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="隶属度占比" min-width="240">
                  <template #default="{ row }">
                    <el-progress :percentage="row?.percent ?? 0" :color="row?.color" :stroke-width="12" />
                  </template>
                </el-table-column>
                <el-table-column label="判定" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row?.isMax" type="success" effect="plain" size="small">最大隶属</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="vectorMeta.length" class="vector-meta">
                <span class="ees-hint">原始向量（后端保存值）：</span>
                <span class="ees-mono">{{ vectorMeta.join(' , ') }}</span>
              </div>
            </div>

            <!-- AHP / COMBO：一致性检验 -->
            <div v-if="isAhpFamily" class="sub-card">
              <div class="sub-title">一致性检验（AHP）</div>
              <div class="ees-hint">
                CR &lt; {{ fmtNum(crThreshold, 4) }} 视为一致性检验通过；CR 超限时后端会阻止正式评估，或按参数允许强制计算。
              </div>
              <el-table
                :data="consistencyRows"
                border
                size="small"
                empty-text="本次结果的算法中间结果中未保存一致性检验记录"
              >
                <el-table-column label="节点" min-width="130" show-overflow-tooltip>
                  <template #default="{ row }">{{ row?.nodeName || row?.nodeCode || '-' }}</template>
                </el-table-column>
                <el-table-column label="阶数" width="80" align="center">
                  <template #default="{ row }">{{ row?.matrixSize ?? '-' }}</template>
                </el-table-column>
                <el-table-column label="λmax" width="110" align="right">
                  <template #default="{ row }">{{ fmtNum(row?.lambdaMax, 6) }}</template>
                </el-table-column>
                <el-table-column label="CI" width="110" align="right">
                  <template #default="{ row }">{{ fmtNum(row?.ci, 6) }}</template>
                </el-table-column>
                <el-table-column label="RI" width="110" align="right">
                  <template #default="{ row }">{{ fmtNum(row?.ri, 6) }}</template>
                </el-table-column>
                <el-table-column label="CR" width="110" align="right">
                  <template #default="{ row }">
                    <span class="ees-score">{{ fmtNum(row?.cr, 6) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="判定" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag :type="row?.passed ? 'success' : 'danger'" effect="plain" size="small">
                      {{ row?.passed ? '通过' : '未通过' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="说明" min-width="180" show-overflow-tooltip>
                  <template #default="{ row }">{{ row?.warning || '-' }}</template>
                </el-table-column>
              </el-table>
              <div v-if="compositeWeights.length" class="sub-card sub-card--inner">
                <div class="sub-title">总排序权重（AHP 组合权重）</div>
                <el-table :data="compositeWeights" border size="small" empty-text="无组合权重数据">
                  <el-table-column prop="code" label="指标编码" width="180">
                    <template #default="{ row }">
                      <span class="ees-mono">{{ row?.code || '-' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="权重" align="right">
                    <template #default="{ row }">{{ fmtWeight(row?.weight) }}</template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-tab-pane>

          <!-- ============ 表格 ============ -->
          <el-tab-pane name="table">
            <template #label>
              <span class="tab-label"><el-icon><Grid /></el-icon>表格</span>
            </template>

            <div class="ees-filter-bar">
              <el-input
                v-model="detailQuery.keyword"
                class="detail-keyword"
                placeholder="指标编码 / 指标名称关键字"
                clearable
                @keyup.enter="searchDetails"
              />
              <el-button type="primary" @click="searchDetails">
                <el-icon><Search /></el-icon>
                查询
              </el-button>
              <el-button @click="resetDetails">
                <el-icon><RefreshLeft /></el-icon>
                重置
              </el-button>
              <el-button v-if="canExport" type="primary" plain :loading="exporting" @click="exportReport('EXCEL')">
                <el-icon><Download /></el-icon>
                导出 Excel
              </el-button>
              <div class="filter-tail">
                <span class="ees-hint">合计行基于该结果全部 {{ allDetails.length }} 条指标明细求和</span>
              </div>
            </div>

            <el-table
              v-loading="detailLoading"
              :data="detailRows"
              border
              stripe
              show-summary
              :summary-method="detailSummary"
              empty-text="暂无指标明细数据"
              :default-sort="{ prop: 'indicatorCode', order: 'ascending' }"
              @sort-change="onDetailSortChange"
            >
              <el-table-column prop="indicatorCode" label="指标编码" width="150" sortable="custom" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="ees-mono">{{ row?.indicatorCode || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="indicatorName" label="指标名称" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row?.indicatorName || '-' }}</template>
              </el-table-column>
              <el-table-column label="单位" width="86" align="center">
                <template #default="{ row }">{{ row?.unit || '-' }}</template>
              </el-table-column>
              <el-table-column label="方向" width="94" align="center">
                <template #default="{ row }">
                  <el-tag :type="directionTagType(row?.direction)" effect="plain" size="small">
                    {{ DIRECTION_LABEL[row?.direction] || row?.direction || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="rawValue" label="原始值" width="120" align="right" sortable="custom">
                <template #default="{ row }">{{ fmtNum(row?.rawValue, 4) }}</template>
              </el-table-column>
              <el-table-column prop="normalizedValue" label="标准化值" width="120" align="right" sortable="custom">
                <template #default="{ row }">{{ fmtNum(row?.normalizedValue, 4) }}</template>
              </el-table-column>
              <el-table-column prop="weight" label="权重" width="100" align="right" sortable="custom">
                <template #default="{ row }">{{ fmtWeight(row?.weight) }}</template>
              </el-table-column>
              <el-table-column prop="contribution" label="贡献值" width="110" align="right" sortable="custom">
                <template #default="{ row }">{{ fmtNum(row?.contribution, 4) }}</template>
              </el-table-column>
              <el-table-column prop="score" label="得分" width="100" align="right" sortable="custom">
                <template #default="{ row }">
                  <span class="ees-score">{{ fmtScore(row?.score) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="grade" label="单指标等级" width="110" align="center">
                <template #default="{ row }">
                  <LevelTag :level="row?.grade" />
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="150">
                <template #default="{ row }">
                  <el-tooltip v-if="row?.remark" :content="String(row.remark)" placement="top" effect="dark">
                    <span class="remark-cell">{{ row.remark }}</span>
                  </el-tooltip>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="ees-pagination">
              <el-pagination
                v-model:current-page="detailQuery.page"
                v-model:page-size="detailQuery.size"
                :total="detailTotal"
                :page-sizes="[20, 50, 100, 200]"
                background
                layout="total, sizes, prev, pager, next, jumper"
                @current-change="loadDetails"
                @size-change="onDetailSizeChange"
              />
            </div>
          </el-tab-pane>

          <!-- ============ 图形 ============ -->
          <el-tab-pane name="chart" lazy>
            <template #label>
              <span class="tab-label"><el-icon><PieChart /></el-icon>图形</span>
            </template>

            <div class="chart-toolbar">
              <div class="toolbar-left">
                <span class="toolbar-label">散点图坐标轴</span>
                <el-select
                  v-model="scatterX"
                  class="scatter-select"
                  size="small"
                  placeholder="横轴指标"
                  :disabled="scatterIndicators.length < 2"
                >
                  <el-option
                    v-for="opt in scatterIndicators"
                    :key="`x-${opt.code}`"
                    :label="indicatorLabel(opt)"
                    :value="opt.code"
                  />
                </el-select>
                <span class="toolbar-sep">/</span>
                <el-select
                  v-model="scatterY"
                  class="scatter-select"
                  size="small"
                  placeholder="纵轴指标"
                  :disabled="scatterIndicators.length < 2"
                >
                  <el-option
                    v-for="opt in scatterIndicators"
                    :key="`y-${opt.code}`"
                    :label="indicatorLabel(opt)"
                    :value="opt.code"
                  />
                </el-select>
                <el-button size="small" :loading="scatterLoading" :disabled="scatterIndicators.length < 2" @click="refreshScatter">
                  应用
                </el-button>
              </div>
              <div class="toolbar-right">
                <el-button size="small" :loading="exportingAll" @click="exportAllCharts">
                  <el-icon><Download /></el-icon>
                  导出全部图形为图片
                </el-button>
              </div>
            </div>

            <div class="ees-hint chart-note">
              当前散点图：X = {{ scatterActualName('x') }}，Y = {{ scatterActualName('y') }}。{{ scatterHint }}
              每张图形卡片右上角亦可单独导出 PNG 图片。
            </div>

            <div ref="chartGridRef" class="ees-chart-grid">
              <ChartCard :chart="charts.bar" />
              <ChartCard :chart="charts.line" />
              <ChartCard :chart="charts.pie" />
              <ChartCard :chart="charts.radar" />
              <ChartCard :chart="charts.scatter" />
              <ChartCard :chart="charts.heatmap" />
              <ChartCard :chart="charts.boxplot" />
              <ChartCard :chart="charts.rankBar" />
            </div>

            <div class="ees-hint chart-foot-note">
              八类图形的数据全部来自后端 `/api/evaluation-results/charts`，前端不做任何数值重算；数据不足时后端返回原因，页面据此展示而不显示误导性空图。
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 4. 追溯信息 -->
      <div class="ees-card">
        <div class="sub-title">可追溯信息（数据快照 · 指标体系版本 · 算法版本 · 参数 · 操作人）</div>
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="任务编号">
            <span class="ees-mono">{{ trace.taskCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="任务名称">{{ trace.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目编码">
            <span class="ees-mono">{{ trace.projectCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="项目名称">{{ trace.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="指标体系版本">
            <span class="ees-mono">{{ trace.indicatorVersionCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="数据快照">
            <span class="ees-mono">{{ trace.snapshotCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="算法">
            {{ ALGORITHM_LABEL[trace.algorithm] || trace.algorithmLabel || trace.algorithm || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="算法版本">
            <span class="ees-mono">{{ trace.algorithmVersion || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="提交人">{{ trace.submitBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ fmtDateTime(trace.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ fmtDateTime(trace.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ fmtDateTime(trace.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ fmtDuration(trace.durationMs) }}</el-descriptions-item>
          <el-descriptions-item label="任务状态">
            <el-tag :type="taskStatusTagType(trace.status)" effect="plain" size="small">
              {{ TASK_STATUS_LABEL[trace.status] || trace.status || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="结果审核 / 发布">
            <el-tag :type="reviewTagType(result.reviewed)" effect="plain" size="small">
              {{ REVIEW_LABEL[result.reviewed] || result.reviewed || '未提交' }}
            </el-tag>
            <el-tag :type="publishTagType(result.publishStatus)" effect="plain" size="small" style="margin-left: 6px">
              {{ PUBLISH_LABEL[result.publishStatus] || result.publishStatus || '未发布' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse class="trace-collapse">
          <el-collapse-item name="parameters">
            <template #title>
              <span class="collapse-title">算法参数（parameters）</span>
            </template>
            <pre class="ees-pre">{{ prettyJson(trace.parameters) }}</pre>
          </el-collapse-item>
          <el-collapse-item name="inputSummary">
            <template #title>
              <span class="collapse-title">输入概要（inputSummary）</span>
            </template>
            <pre class="ees-pre">{{ prettyJson(trace.inputSummary) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 5. 结果原始数据 -->
      <el-collapse class="ees-card result-raw">
        <el-collapse-item name="extra">
          <template #title>
            <span class="collapse-title">结果原始数据（算法中间结果 extra）</span>
          </template>
          <pre class="ees-pre">{{ prettyJson(extra) }}</pre>
        </el-collapse-item>
      </el-collapse>
    </template>

    <!-- 结果复核 -->
    <el-dialog v-model="reviewVisible" title="结果复核" width="560px" :close-on-click-modal="false">
      <div class="review-target">
        <div class="review-line">
          <span class="review-label">对象</span>
          <span class="ees-mono">{{ result?.objectCode || '-' }}</span>
          <span class="review-name">{{ result?.objectName || '' }}</span>
        </div>
        <div class="review-line">
          <span class="review-label">当前状态</span>
          <el-tag :type="reviewTagType(result?.reviewed)" effect="plain" size="small">
            {{ REVIEW_LABEL[result?.reviewed || 'NONE'] || '未提交' }}
          </el-tag>
        </div>
      </div>

      <el-form label-width="88px" style="margin-top: 12px">
        <el-form-item label="复核动作">
          <el-radio-group v-model="reviewForm.action">
            <el-radio-button value="APPROVE">通过</el-radio-button>
            <el-radio-button value="REJECT">退回</el-radio-button>
            <el-radio-button value="SUBMIT">提交审核</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核备注">
          <el-input
            v-model="reviewForm.comment"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="填写复核意见；退回时必须填写具体原因"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewSaving" @click="submitReview">提交复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'
import LevelTag from '@/components/LevelTag.vue'
import ChartCard from '@/components/ChartCard.vue'
import { reportApi, resultApi, type ChartData } from '@/api'
import { useUserStore } from '@/stores/user'
import { GRADE_COLOR, PALETTE } from '@/utils/chartOptions'
import {
  ALGORITHM_LABEL,
  DIRECTION_LABEL,
  fmtDateTime,
  fmtDuration,
  fmtNum,
  fmtScore,
  fmtWeight,
  parseJson,
  PUBLISH_LABEL,
  REVIEW_LABEL,
  TASK_STATUS_LABEL,
  TASK_STATUS_TYPE
} from '@/utils/format'

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'
type ExportType = 'EXCEL' | 'WORD' | 'PDF'

interface VectorRow {
  grade: string
  value: number
  percent: number
  color: string
  isMax: boolean
}

interface ScatterIndicator {
  code: string
  name?: string
}

/** 八类图形的后端键（方案 7.2） */
const CHART_KEYS = ['bar', 'line', 'pie', 'radar', 'scatter', 'heatmap', 'boxplot', 'rankBar'] as const

const EXPORT_LABEL: Record<ExportType, string> = { EXCEL: 'Excel', WORD: 'Word', PDF: 'PDF' }

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const canReview = computed(() => userStore.has('result:review'))
const canPublish = computed(() => userStore.has('result:publish'))
const canExport = computed(() => userStore.has('result:export'))

const resultId = computed(() => {
  const raw = Array.isArray(route.params.id) ? route.params.id[0] : route.params.id
  const n = Number(raw)
  return Number.isFinite(n) ? n : 0
})

function arr(v: any): any[] {
  return Array.isArray(v) ? v : []
}

function prettyJson(v: any): string {
  if (v === null || v === undefined || v === '') return '（无数据）'
  const obj = typeof v === 'string' ? parseJson<any>(v, v) : v
  if (obj === null || obj === undefined) return '（无数据）'
  if (typeof obj === 'string') return obj
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

function directionTagType(d?: string): TagType {
  if (d === 'POSITIVE') return 'success'
  if (d === 'NEGATIVE') return 'danger'
  if (d === 'INTERVAL') return 'primary'
  return 'info'
}

function reviewTagType(status?: string): TagType {
  if (status === 'APPROVED') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}

function publishTagType(status?: string): TagType {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'warning'
  return 'info'
}

function taskStatusTagType(status?: string): TagType {
  const t = status ? TASK_STATUS_TYPE[status] : ''
  if (t === 'primary' || t === 'success' || t === 'info' || t === 'warning' || t === 'danger') return t
  return 'info'
}

/** ---------------- 加载 ---------------- */

const loading = ref(false)
const full = ref<any>(null)
const textView = ref<any>(null)
const charts = ref<Record<string, ChartData>>({})

const result = computed<any>(() => full.value?.result || null)
const extra = computed<Record<string, any>>(() => {
  const v = parseJson<Record<string, any>>(full.value?.extra, {})
  return v && typeof v === 'object' && !Array.isArray(v) ? v : {}
})
const warnings = computed<string[]>(() => arr(full.value?.warnings).map((w) => String(w)))
const parsedVector = computed<number[]>(() =>
  arr(full.value?.parsedVector)
    .map((v) => Number(v))
    .filter((v) => Number.isFinite(v))
)
const trace = computed<any>(() => full.value?.traceability || {})
const allDetails = computed<any[]>(() => arr(full.value?.details))

/** 兼容 full.text 直接为结论对象 / 嵌套 conclusion 两种结构 */
const conclusion = computed<any>(() => {
  const t = full.value?.text
  if (!t || typeof t !== 'object') return {}
  return t.conclusion && typeof t.conclusion === 'object' ? t.conclusion : t
})
const conclusionSummary = computed<string>(
  () => conclusion.value?.summary || '后端未返回文本结论，请确认该任务是否已成功完成算法评估。'
)
const weakIndicators = computed<any[]>(() => arr(conclusion.value?.weakIndicators))
const strongIndicators = computed<any[]>(() => arr(conclusion.value?.strongIndicators))
const improvements = computed<any[]>(() => {
  const fromTextView = arr(textView.value?.improvements)
  return fromTextView.length ? fromTextView : arr(full.value?.text?.improvements)
})

const algorithm = computed<string>(() => String(result.value?.algorithm || ''))
const isFuzzy = computed(() => algorithm.value.toUpperCase() === 'FUZZY')
const isAhpFamily = computed(() => ['AHP', 'COMBO'].includes(algorithm.value.toUpperCase()))

const gradeNames = computed<string[]>(() => arr(extra.value?.gradeNames).map((g) => String(g)))

const headerTitle = computed(() => {
  const code = result.value?.objectCode || '评估结果'
  const name = result.value?.objectName ? `（${result.value.objectName}）` : ''
  const task = result.value?.taskCode ? ` · ${result.value.taskCode}` : ''
  return `${code}${name} 结果分析${task}`
})

const headerDesc = computed(() => {
  const parts: string[] = []
  if (result.value?.projectCode) parts.push(`项目 ${result.value.projectCode}`)
  if (result.value?.indicatorVersionCode) parts.push(`指标体系 ${result.value.indicatorVersionCode}`)
  if (result.value?.snapshotCode) parts.push(`数据快照 ${result.value.snapshotCode}`)
  parts.push('文本 / 表格 / 图形三类展示同源，可追溯信息完整留痕')
  return parts.join(' · ')
})

/** 模糊综合评判向量（隶属度 → 评语） */
const vectorRows = computed<VectorRow[]>(() => {
  const vec = parsedVector.value
  if (!vec.length) return []
  const names = gradeNames.value
  const maxValue = Math.max(...vec)
  return vec.map((raw, i) => {
    const value = Number.isFinite(raw) ? raw : 0
    const grade = names[i] || `评语 ${i + 1}`
    // 隶属度通常在 0~1；若后端已用百分数存放则原样使用
    const asPercent = value <= 1 ? value * 100 : value
    return {
      grade,
      value,
      percent: Math.max(0, Math.min(100, Number(asPercent.toFixed(2)))),
      color: GRADE_COLOR[grade] || PALETTE[i % PALETTE.length],
      isMax: Math.abs(value - maxValue) < 1e-12
    }
  })
})

const vectorMeta = computed<string[]>(() => parsedVector.value.map((v) => fmtNum(v, 6)))

/** AHP / COMBO 一致性检验 */
const consistencyRows = computed<any[]>(() => arr(extra.value?.consistency))
const crThreshold = computed<any>(() => extra.value?.crThreshold ?? 0.1)
const compositeWeights = computed<Array<{ code: string; weight: any }>>(() => {
  const w = extra.value?.compositeWeights
  if (!w || typeof w !== 'object' || Array.isArray(w)) return []
  return Object.entries(w as Record<string, any>).map(([code, weight]) => ({ code, weight }))
})

async function loadFull() {
  const id = resultId.value
  if (!id) {
    full.value = null
    return
  }
  loading.value = true
  try {
    const data = await resultApi.full(id)
    full.value = data || null
    charts.value = data?.charts && typeof data.charts === 'object' ? { ...data.charts } : {}
    syncScatterSelection()
  } catch {
    full.value = null
    charts.value = {}
  } finally {
    loading.value = false
  }
}

/** 文本展示接口：改进建议位于该接口返回体中 */
async function loadTextView() {
  if (!resultId.value) return
  try {
    textView.value = (await resultApi.text(resultId.value)) || null
  } catch {
    textView.value = null
  }
}

/** 图形总集：以图表接口返回的最新数据为准，失败时保留完整视图中的图形 */
async function loadCharts() {
  const r = result.value
  if (!r) return
  try {
    const res = await resultApi.charts({
      taskId: r.taskId,
      resultId: resultId.value,
      projectId: r.projectId
    })
    if (res && typeof res === 'object') {
      const merged: Record<string, ChartData> = { ...charts.value }
      CHART_KEYS.forEach((key) => {
        const chart = (res as Record<string, ChartData>)[key]
        if (chart) merged[key] = chart
      })
      charts.value = merged
      syncScatterSelection()
    }
  } catch {
    // 统一响应拦截器已提示；保留完整视图中的图形数据
  }
}

async function reloadAll() {
  await loadFull()
  await Promise.all([loadTextView(), loadCharts(), loadDetails()])
}

function goBack() {
  router.push('/results')
}

function printPage() {
  ElMessage.info('即将打开浏览器打印对话框，选择「另存为 PDF」可导出 PDF 文件')
  window.print()
}

/** ---------------- 散点图坐标轴控制 ---------------- */

const scatterX = ref('')
const scatterY = ref('')
const scatterLoading = ref(false)
const scatterRequested = ref<{ x: string; y: string }>({ x: '', y: '' })

const scatterChartData = computed<ChartData | undefined>(() => charts.value.scatter)
const scatterIndicators = computed<ScatterIndicator[]>(() => {
  const list = arr(scatterChartData.value?.availableIndicators)
  return list
    .filter((i) => i && (i.code || i.name))
    .map((i) => ({ code: String(i.code ?? i.name), name: i.name ? String(i.name) : undefined }))
})

function indicatorLabel(opt: ScatterIndicator): string {
  return opt.name ? `${opt.name}（${opt.code}）` : opt.code
}

function syncScatterSelection() {
  const chart = scatterChartData.value
  if (!chart) return
  const x = chart.xIndicatorCode ? String(chart.xIndicatorCode) : ''
  const y = chart.yIndicatorCode ? String(chart.yIndicatorCode) : ''
  scatterX.value = x
  scatterY.value = y
  scatterRequested.value = { x, y }
}

function scatterActualName(axis: 'x' | 'y'): string {
  const chart = scatterChartData.value
  if (!chart || chart.emptyReason) return '-'
  return axis === 'x'
    ? String(chart.xIndicatorName || chart.xIndicatorCode || '-')
    : String(chart.yIndicatorName || chart.yIndicatorCode || '-')
}

const scatterHint = computed<string>(() => {
  const chart = scatterChartData.value
  if (!chart) return '散点图数据尚未返回。'
  if (chart.emptyReason) return '该图形数据不足，后端已给出原因。'
  const req = scatterRequested.value
  if (!req.x || !req.y) return '坐标轴由后端默认选择。'
  if (String(chart.xIndicatorCode) === req.x && String(chart.yIndicatorCode) === req.y) {
    return '已按所选指标组合展示。'
  }
  return '后端当前的散点图图形接口仅接受任务 / 结果 / 项目参数，实际返回的是下方所示坐标轴组合，如需其他组合请在评估时调整指标体系。'
})

async function refreshScatter() {
  const r = result.value
  if (!r) return
  scatterRequested.value = { x: scatterX.value, y: scatterY.value }
  scatterLoading.value = true
  try {
    const res = await resultApi.chart('scatter', {
      taskId: r.taskId,
      resultId: resultId.value,
      projectId: r.projectId
    })
    const chart = res?.scatter
    if (chart) charts.value = { ...charts.value, scatter: chart }
  } catch {
    // 统一响应拦截器已提示
  } finally {
    scatterLoading.value = false
  }
}

/** ---------------- 图形导出 ---------------- */

const chartGridRef = ref<HTMLElement | null>(null)
const exportingAll = ref(false)

/**
 * 逐张导出当前已渲染的图形：直接读取 ECharts 实例的 DataURL，
 * 不做任何数值重算；单张卡片右上角亦有独立导出按钮。
 */
async function exportAllCharts() {
  exportingAll.value = true
  try {
    await nextTick()
    const root: ParentNode = chartGridRef.value || document
    const cards = Array.from(root.querySelectorAll<HTMLElement>('.ees-chart-card'))
    const targets: Array<{ title: string; host: HTMLElement }> = []
    cards.forEach((card, ci) => {
      const cardTitle = card.querySelector('.ees-chart-title')?.textContent?.trim() || `图形${ci + 1}`
      const hosts = Array.from(card.querySelectorAll<HTMLElement>('.ees-chart-host'))
      hosts.forEach((host, hi) => {
        targets.push({ title: hosts.length > 1 ? `${cardTitle}-${hi + 1}` : cardTitle, host })
      })
    })

    let exported = 0
    for (const target of targets) {
      const instance = echarts.getInstanceByDom(target.host)
      const url = instance?.getDataURL({ pixelRatio: 2, backgroundColor: '#ffffff' })
      if (!url) continue
      const link = document.createElement('a')
      link.href = url
      link.download = `${target.title}.png`
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      exported += 1
      await new Promise((resolve) => window.setTimeout(resolve, 300))
    }

    if (exported === 0) {
      ElMessage.warning('当前没有已渲染的图形，可切换至图形标签页后重试，或使用每张卡片右上角的导出按钮')
    } else {
      ElMessage.success(`已导出 ${exported} 张图形图片`)
    }
  } finally {
    exportingAll.value = false
  }
}

/** ---------------- 表格明细（服务端排序 / 筛选 / 分页） ---------------- */

const detailLoading = ref(false)
const detailRows = ref<any[]>([])
const detailTotal = ref(0)

const SORT_FIELD: Record<string, string> = {
  indicatorCode: 'indicatorCode',
  indicatorName: 'indicatorCode',
  rawValue: 'rawValue',
  normalizedValue: 'normalizedValue',
  weight: 'weight',
  contribution: 'contribution',
  score: 'score'
}

const detailQuery = reactive({
  keyword: '',
  sortBy: 'indicatorCode',
  order: 'asc' as 'asc' | 'desc',
  page: 1,
  size: 50
})

async function loadDetails() {
  if (!resultId.value) return
  detailLoading.value = true
  try {
    const res = await resultApi.details(resultId.value, {
      keyword: detailQuery.keyword ? detailQuery.keyword.trim() : undefined,
      sortBy: detailQuery.sortBy,
      order: detailQuery.order,
      page: detailQuery.page,
      size: detailQuery.size
    })
    detailRows.value = arr(res?.records)
    detailTotal.value = Number(res?.total ?? 0)
  } catch {
    detailRows.value = []
    detailTotal.value = 0
  } finally {
    detailLoading.value = false
  }
}

function searchDetails() {
  detailQuery.page = 1
  loadDetails()
}

function resetDetails() {
  detailQuery.keyword = ''
  detailQuery.sortBy = 'indicatorCode'
  detailQuery.order = 'asc'
  detailQuery.page = 1
  loadDetails()
}

function onDetailSizeChange() {
  detailQuery.page = 1
  loadDetails()
}

function onDetailSortChange(payload: { prop?: string | null; order?: string | null }) {
  const prop = payload?.prop || 'indicatorCode'
  detailQuery.sortBy = SORT_FIELD[prop] || 'indicatorCode'
  detailQuery.order = payload?.order === 'descending' ? 'desc' : 'asc'
  detailQuery.page = 1
  loadDetails()
}

/** 合计行：Σ权重 / Σ贡献值，取该结果全部指标明细（非仅当前页） */
function detailSummary(param: any): string[] {
  const columns: any[] = arr(param?.columns)
  const rows = allDetails.value
  const sumWeight = rows.reduce((sum, d) => sum + (Number(d?.weight) || 0), 0)
  const sumContribution = rows.reduce((sum, d) => sum + (Number(d?.contribution) || 0), 0)
  return columns.map((col: any, index: number) => {
    if (index === 0) return '合计'
    if (col?.property === 'weight') return `Σ ${fmtWeight(sumWeight)}`
    if (col?.property === 'contribution') return `Σ ${fmtNum(sumContribution, 4)}`
    return ''
  })
}

/** ---------------- 导出与复核发布 ---------------- */

const exporting = ref(false)

async function onExportCommand(command: unknown) {
  if (command === 'EXCEL' || command === 'WORD' || command === 'PDF') {
    await exportReport(command)
  }
}

async function exportReport(type: ExportType) {
  const r = result.value
  if (!r || !resultId.value) return
  exporting.value = true
  try {
    const res = await resultApi.exportAs(resultId.value, type)
    if (!res?.reportId) {
      ElMessage.warning('导出未返回报告编号，请稍后在报告导出页面查看')
      return
    }
    await reportApi.download(res.reportId, res.fileName || `评估结果_${r.objectCode || resultId.value}`)
    ElMessage.success(`已导出 ${EXPORT_LABEL[type]}：${res.fileName || '报告'}`)
  } catch {
    // 统一响应拦截器已提示
  } finally {
    exporting.value = false
  }
}

const reviewVisible = ref(false)
const reviewSaving = ref(false)
const reviewForm = reactive({ action: 'APPROVE' as string, comment: '' })

function openReview() {
  const r = result.value
  if (!r) return
  reviewForm.action = r.reviewed === 'PENDING' ? 'APPROVE' : 'SUBMIT'
  reviewForm.comment = r.reviewComment || ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!resultId.value) return
  if (reviewForm.action === 'REJECT' && !reviewForm.comment.trim()) {
    ElMessage.warning('退回操作必须填写复核备注，便于评估人员定位问题')
    return
  }
  reviewSaving.value = true
  try {
    await resultApi.review(resultId.value, reviewForm.action, reviewForm.comment.trim() || undefined)
    ElMessage.success('复核操作完成')
    reviewVisible.value = false
    await loadFull()
  } catch {
    // 统一响应拦截器已提示
  } finally {
    reviewSaving.value = false
  }
}

async function publishResult() {
  const r = result.value
  if (!r || !resultId.value) return
  try {
    await ElMessageBox.confirm(
      `发布后「${r.objectCode || '该对象'}」的评估结果将作为正式结果对外提供，确定发布吗？`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确定发布', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await resultApi.publish(resultId.value)
    ElMessage.success('结果已发布')
    await loadFull()
  } catch {
    // 统一响应拦截器已提示
  }
}

async function archiveResult() {
  const r = result.value
  if (!r || !resultId.value) return
  try {
    await ElMessageBox.confirm(
      '归档后该结果将转入历史留痕，不再作为对外正式结果展示。确定归档吗？',
      '归档确认',
      { type: 'warning', confirmButtonText: '确定归档', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await resultApi.archive(resultId.value)
    ElMessage.success('结果已归档')
    await loadFull()
  } catch {
    // 统一响应拦截器已提示
  }
}

/** ---------------- 初始化 ---------------- */

const activeTab = ref<'text' | 'table' | 'chart'>('text')

onMounted(async () => {
  if (!resultId.value) {
    ElMessage.error('结果编号无效，无法加载评估结果')
    return
  }
  await loadFull()
  await Promise.all([loadTextView(), loadCharts(), loadDetails()])
})
</script>

<style scoped>
.page-note {
  margin-bottom: 12px;
}

.warn-block {
  margin-bottom: 14px;
}

.warn-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
}

.ees-stat-value .ees-level-tag {
  font-size: 16px;
  padding: 2px 14px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.conclusion-card {
  background: linear-gradient(135deg, #f7fbff 0%, #eef5ff 100%);
  border: 1px solid #dbe8fb;
  border-left: 4px solid var(--ees-primary);
  border-radius: var(--ees-radius-sm);
  padding: 14px 18px;
  margin-bottom: 14px;
}

.conclusion-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ees-primary);
  margin-bottom: 6px;
}

.conclusion-text {
  margin: 0 0 8px;
  font-size: 15px;
  line-height: 1.9;
  color: var(--ees-text);
  text-indent: 0;
}

.text-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

@media (max-width: 1100px) {
  .text-grid {
    grid-template-columns: 1fr;
  }
}

.sub-card {
  margin-bottom: 14px;
}

.sub-card--inner {
  margin-top: 12px;
  margin-bottom: 0;
  padding-top: 10px;
  border-top: 1px dashed var(--ees-border);
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ees-text);
  margin: 2px 0 10px;
}

.improve-text {
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--ees-text);
}

.vector-meta {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-keyword {
  width: 240px;
}

.filter-tail {
  margin-left: auto;
}

.remark-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  color: var(--ees-text-secondary);
}

.chart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #f7f9fd;
  border: 1px solid var(--ees-border);
  border-radius: var(--ees-radius-sm);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.toolbar-label {
  font-size: 13px;
  color: var(--ees-text-secondary);
}

.toolbar-sep {
  color: var(--ees-text-muted);
}

.scatter-select {
  width: 200px;
}

.chart-note {
  margin-bottom: 10px;
}

.chart-foot-note {
  margin-top: 12px;
}

.trace-collapse {
  margin-top: 12px;
  border-top: 1px solid var(--ees-border);
}

.collapse-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
}

.result-raw {
  padding: 4px 16px;
}

.review-target {
  background: #f7f9fd;
  border: 1px solid var(--ees-border);
  border-radius: var(--ees-radius-sm);
  padding: 10px 14px;
}

.review-line {
  display: flex;
  align-items: center;
  gap: 8px;
  line-height: 2;
  font-size: 13px;
}

.review-label {
  width: 62px;
  color: var(--ees-text-secondary);
  flex-shrink: 0;
}

.review-name {
  color: var(--ees-text-secondary);
}
</style>

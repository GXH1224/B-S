<template>
  <div class="ees-page">
    <PageHeader
      title="数据接入"
      desc="文件导入支持 XLSX 与 CSV，导入后执行必填、范围、格式、方向、重复、缺失与单位校验，并生成质量报告与错误明细"
    >
      <template #actions>
        <el-button :loading="downloading" @click="downloadTemplate">下载数据模板</el-button>
        <el-button v-if="canImport" type="primary" @click="openImport">
          <el-icon><Upload /></el-icon>
          <span>导入数据文件</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="ees-card">
      <div class="ees-filter-bar">
        <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 220px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.code || ''} ${p.name || ''}`.trim()" :value="p.id" />
        </el-select>
        <el-select v-model="query.sourceType" placeholder="来源类型" clearable style="width: 150px">
          <el-option label="文件导入" value="FILE" />
          <el-option label="数据库抽取" value="DATABASE" />
        </el-select>
        <el-select v-model="query.status" placeholder="批次状态" clearable style="width: 150px">
          <el-option v-for="(label, value) in BATCH_STATUS_LABEL" :key="value" :label="label" :value="value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="批次编号 / 来源"
          clearable
          style="width: 200px"
          @keyup.enter="search()"
        />
        <el-button type="primary" @click="search()">查询</el-button>
        <el-button @click="reset()">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe>
        <template #empty>
          <div class="ees-empty">
            暂无数据批次，可点击“导入数据文件”上传 XLSX / CSV，或在数据源管理中执行只读抽取。
          </div>
        </template>
        <el-table-column prop="batchCode" label="批次编号" width="190" fixed="left" show-overflow-tooltip />
        <el-table-column label="来源类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'DATABASE' ? 'warning' : 'primary'" size="small" effect="plain">
              {{ SOURCE_TYPE_LABEL[row.sourceType] || row.sourceType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="sourceText(row)" placement="top" :show-after="300">
              <span class="ellipsis">{{ sourceText(row) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="projectCode" label="项目编码" width="130" show-overflow-tooltip />
        <el-table-column label="总行数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.totalRows, 0) }}</template>
        </el-table-column>
        <el-table-column label="有效行" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.validRows, 0) }}</template>
        </el-table-column>
        <el-table-column label="错误行" width="90" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': Number(row.errorRows) > 0 }">{{ fmtNum(row.errorRows, 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="对象数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.objectCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="指标数" width="90" align="right">
          <template #default="{ row }">{{ fmtNum(row.indicatorCount, 0) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="batchStatusType(row.status)" size="small" effect="plain">
              {{ batchStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="导入人" width="110" show-overflow-tooltip />
        <el-table-column label="导入时间" width="165">
          <template #default="{ row }">{{ fmtDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="op-cell">
              <el-button link type="primary" size="small" @click="openQuality(row)">质量报告</el-button>
              <el-button link type="primary" size="small" @click="openErrors(row)">错误明细</el-button>
              <el-button link type="primary" size="small" @click="openPreview(row)">数据预览</el-button>
              <el-dropdown
                v-if="canValidate || canSnapshot || canDelete"
                trigger="click"
                @command="(cmd: string) => onRowCommand(cmd, row)"
              >
                <el-button link type="primary" size="small">
                  更多<el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="canValidate" command="revalidate">重新校验</el-dropdown-item>
                    <el-dropdown-item v-if="canSnapshot" command="snapshot">生成数据快照</el-dropdown-item>
                    <el-dropdown-item v-if="canDelete" command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="ees-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load()"
          @size-change="search()"
        />
      </div>
    </div>

    <!-- 导入数据文件 -->
    <el-dialog v-model="importVisible" title="导入数据文件" width="940px" top="5vh" @closed="resetImport">
      <div class="dialog-body">
        <template v-if="importStage === 'form'">
          <el-form label-width="170px">
            <el-form-item label="目标项目">
              <el-select v-model="importForm.projectId" placeholder="请选择数据归属项目" clearable filterable style="width: 300px" @change="onImportProjectChange">
                <el-option v-for="p in projects" :key="p.id" :label="`${p.code || ''} ${p.name || ''}`.trim()" :value="p.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="校验依据的指标体系版本">
              <el-select v-model="importForm.targetVersionId" placeholder="请选择指标体系版本" clearable filterable style="width: 320px">
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`${v.versionCode || ''} ${v.versionName || ''}`.trim()"
                  :value="v.id"
                />
              </el-select>
              <div class="ees-hint">用于校验指标编码覆盖度、取值范围、方向与单位一致性。</div>
              <!-- 版本为空时必须解释原因：指标体系版本在导入时绑定项目，
                   选了未绑定任何版本的项目时下拉会是空的，而界面上原本没有任何提示 -->
              <el-alert
                v-if="importForm.projectId && !versions.length"
                type="warning"
                :closable="false"
                show-icon
                title="所选项目下没有可用的指标体系版本"
                description="指标体系版本在导入时绑定项目：请到「指标体系 → 导入指标体系」导入，并把「关联项目」选为本项目；若希望某个版本对所有项目可见，导入时把「关联项目」留空即可（公共指标体系）。也可以先把「目标项目」改成已绑定指标体系版本的项目。"
              />
            </el-form-item>
            <el-form-item label="数据文件">
              <el-upload
                ref="uploadRef"
                v-model:file-list="fileList"
                :auto-upload="false"
                :limit="1"
                accept=".xlsx,.xls,.csv"
                :on-change="onFileChange"
                :on-exceed="onFileExceed"
                class="data-upload"
              >
                <el-button type="primary" plain>
                  <el-icon><FolderOpened /></el-icon>
                  <span>选择数据文件</span>
                </el-button>
                <template #tip>
                  <div class="ees-hint">
                    支持 XLSX、XLS 与 CSV；模板列：project_code, object_code, object_name, data_time,
                    indicator_code, indicator_value, unit, source_batch, remark
                  </div>
                </template>
              </el-upload>
            </el-form-item>
          </el-form>
        </template>

        <template v-else>
          <el-alert
            :type="importResult?.blocking ? 'error' : 'success'"
            :closable="false"
            show-icon
            :title="importResult?.blocking ? '导入完成，存在阻断级错误' : '导入成功'"
            :description="`批次编号：${importResult?.batchCode || '-'}；状态：${batchStatusLabel(importResult?.status)}`"
          />

          <div class="ees-stat-grid report-grid">
            <StatCard label="总行数" :value="fmtNum(report.totalRows, 0)" variant="primary" />
            <StatCard label="有效行" :value="fmtNum(report.validRows, 0)" variant="success" />
            <StatCard label="错误行" :value="fmtNum(report.errorRows, 0)" variant="danger" />
            <StatCard label="对象数" :value="fmtNum(report.objectCount, 0)" />
            <StatCard label="指标数" :value="fmtNum(report.distinctIndicatorCount, 0)" />
            <StatCard label="缺失值" :value="fmtNum(report.missingValueCount, 0)" variant="warning" />
            <StatCard label="越界" :value="fmtNum(report.outOfRangeCount, 0)" variant="warning" />
            <StatCard label="单位不一致" :value="fmtNum(report.unitMismatchCount, 0)" variant="warning" />
            <StatCard label="格式非法" :value="fmtNum(report.formatErrorCount, 0)" variant="danger" />
            <StatCard label="必填缺失" :value="fmtNum(report.requiredFieldMissingCount, 0)" variant="danger" />
            <StatCard label="重复记录" :value="fmtNum(report.duplicateCount, 0)" variant="warning" />
          </div>

          <div class="blocking-line">
            <span class="blocking-label">校验结论</span>
            <el-tag :type="report.blocking ? 'danger' : 'success'" size="small" effect="dark">
              {{ report.blocking ? '阻断' : '可继续' }}
            </el-tag>
            <span class="ees-hint">
              {{ report.blocking ? '存在阻断级质量问题，需修正数据后重新导入。' : '未发现阻断级质量问题，可继续生成数据快照。' }}
            </span>
          </div>

          <el-alert
            v-for="(reason, idx) in report.blockingReasons || []"
            :key="idx"
            type="error"
            :closable="false"
            show-icon
            :title="reason"
            class="reason-alert"
          />

          <div class="result-title">错误明细（最多展示 200 条）</div>
          <el-table :data="importResult?.errors || []" border size="small" max-height="320" empty-text="没有错误行">
            <el-table-column label="行号" width="90" align="right">
              <template #default="{ row }">{{ fmtNum(row.row, 0) }}</template>
            </el-table-column>
            <el-table-column label="字段" width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.field || '-' }}</template>
            </el-table-column>
            <el-table-column label="说明" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ row.message || '-' }}</template>
            </el-table-column>
            <el-table-column label="级别" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="severityMeta(row.severity).type" size="small" effect="plain">
                  {{ severityMeta(row.severity).label }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="result-actions">
            <el-button :loading="exporting" @click="exportErrors(importResult?.batchId)">下载错误明细</el-button>
            <el-link type="primary" :underline="false" href="#/snapshots">生成数据快照 →</el-link>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button v-if="importStage === 'result'" @click="importVisible = false">关闭</el-button>
        <template v-else>
          <el-button @click="importVisible = false">取消</el-button>
          <el-button type="primary" :loading="importing" @click="submitImport">开始导入</el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 质量报告 -->
    <el-dialog v-model="qualityVisible" title="数据质量报告" width="900px" top="6vh">
      <div v-loading="qualityLoading" class="dialog-body">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="批次编号">
            <span class="ees-mono">{{ qualityBatch?.batchCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="项目编码">{{ qualityBatch?.projectCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="批次状态">{{ batchStatusLabel(qualityBatch?.status) }}</el-descriptions-item>
        </el-descriptions>

        <div class="ees-stat-grid report-grid">
          <StatCard label="总行数" :value="fmtNum(qualityReport?.totalRows, 0)" variant="primary" />
          <StatCard label="有效行" :value="fmtNum(qualityReport?.validRows, 0)" variant="success" />
          <StatCard label="错误行" :value="fmtNum(qualityReport?.errorRows, 0)" variant="danger" />
          <StatCard label="对象数" :value="fmtNum(qualityReport?.objectCount, 0)" />
          <StatCard label="指标数" :value="fmtNum(qualityReport?.distinctIndicatorCount, 0)" />
          <StatCard label="缺失值" :value="fmtNum(qualityReport?.missingValueCount, 0)" variant="warning" />
          <StatCard label="越界" :value="fmtNum(qualityReport?.outOfRangeCount, 0)" variant="warning" />
          <StatCard label="单位不一致" :value="fmtNum(qualityReport?.unitMismatchCount, 0)" variant="warning" />
          <StatCard label="格式非法" :value="fmtNum(qualityReport?.formatErrorCount, 0)" variant="danger" />
          <StatCard label="必填缺失" :value="fmtNum(qualityReport?.requiredFieldMissingCount, 0)" variant="danger" />
          <StatCard label="重复记录" :value="fmtNum(qualityReport?.duplicateCount, 0)" variant="warning" />
        </div>

        <div class="blocking-line">
          <span class="blocking-label">校验结论</span>
          <el-tag :type="qualityReport?.blocking ? 'danger' : 'success'" size="small" effect="dark">
            {{ qualityReport?.blocking ? '阻断' : '可继续' }}
          </el-tag>
          <span class="ees-hint">校验时间：{{ fmtDateTime(qualityReport?.checkedAt) }}</span>
        </div>

        <el-alert
          v-for="(reason, idx) in qualityReport?.blockingReasons || []"
          :key="idx"
          type="error"
          :closable="false"
          show-icon
          :title="reason"
          class="reason-alert"
        />

        <div v-if="severityEntries.length" class="result-title">规则级别配置</div>
        <el-table v-if="severityEntries.length" :data="severityEntries" border size="small">
          <el-table-column label="规则类型" min-width="160">
            <template #default="{ row }">{{ ruleTypeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="级别" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="severityMeta(row.severity).type" size="small" effect="plain">
                {{ severityMeta(row.severity).label }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="result-title">错误明细（最多 500 条）</div>
        <el-table :data="qualityErrors" border size="small" max-height="300" empty-text="没有错误行">
          <el-table-column label="行号" width="90" align="right">
            <template #default="{ row }">{{ fmtNum(row.row, 0) }}</template>
          </el-table-column>
          <el-table-column label="字段" width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.field || '-' }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">{{ row.message || '-' }}</template>
          </el-table-column>
          <el-table-column label="级别" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="severityMeta(row.severity).type" size="small" effect="plain">
                {{ severityMeta(row.severity).label }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button :loading="exporting" @click="exportErrors(qualityBatch?.id)">下载错误明细</el-button>
        <el-button type="primary" @click="qualityVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 错误明细 -->
    <el-dialog v-model="errorsVisible" title="错误行明细" width="1000px" top="6vh">
      <div v-loading="errorsLoading" class="dialog-body">
        <div class="ees-filter-bar">
          <span class="ees-hint">
            批次 {{ errorsBatch?.batchCode || '-' }} 共 {{ fmtNum(errorsList.length, 0) }} 条错误行；错误行不参与数据快照生成。
          </span>
        </div>
        <el-table :data="errorsList" border size="small" max-height="460" empty-text="没有错误行">
          <el-table-column label="行号" width="90" align="right">
            <template #default="{ row }">{{ fmtNum(row.rowNo, 0) }}</template>
          </el-table-column>
          <el-table-column prop="projectCode" label="项目编码" width="120" show-overflow-tooltip />
          <el-table-column prop="objectCode" label="对象编码" width="130" show-overflow-tooltip />
          <el-table-column label="数据时间" width="165">
            <template #default="{ row }">{{ fmtDateTime(row.dataTime) }}</template>
          </el-table-column>
          <el-table-column prop="indicatorCode" label="指标编码" width="130" show-overflow-tooltip />
          <el-table-column label="指标值" width="120" show-overflow-tooltip>
            <template #default="{ row }">
              {{ row.indicatorValue === null || row.indicatorValue === undefined ? '-' : row.indicatorValue }}
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="90" show-overflow-tooltip />
          <el-table-column label="错误说明" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMsg || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button :loading="exporting" @click="exportErrors(errorsBatch?.id)">导出错误明细</el-button>
        <el-button type="primary" @click="errorsVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 数据预览 -->
    <el-dialog v-model="previewVisible" title="原始数据预览" width="1080px" top="6vh">
      <div v-loading="previewLoading" class="dialog-body">
        <div class="ees-filter-bar">
          <span class="ees-hint">
            批次 {{ previewBatch?.batchCode || '-' }} 最多预览 200 条原始记录；原始数据与标准化数据分别保存，标准化结果见数据快照。
          </span>
        </div>
        <el-table :data="previewRows" border size="small" max-height="460" empty-text="没有可预览的记录">
          <el-table-column label="行号" width="80" align="right">
            <template #default="{ row }">{{ fmtNum(row.rowNo, 0) }}</template>
          </el-table-column>
          <el-table-column prop="projectCode" label="项目编码" width="110" show-overflow-tooltip />
          <el-table-column prop="objectCode" label="对象编码" width="120" show-overflow-tooltip />
          <el-table-column prop="objectName" label="对象名称" width="140" show-overflow-tooltip />
          <el-table-column label="数据时间" width="165">
            <template #default="{ row }">{{ fmtDateTime(row.dataTime) }}</template>
          </el-table-column>
          <el-table-column prop="indicatorCode" label="指标编码" width="130" show-overflow-tooltip />
          <el-table-column prop="indicatorName" label="指标名称" width="150" show-overflow-tooltip />
          <el-table-column label="指标值" width="120" align="right">
            <template #default="{ row }">
              {{ row.indicatorValue === null || row.indicatorValue === undefined ? '-' : row.indicatorValue }}
            </template>
          </el-table-column>
          <el-table-column prop="unit" label="单位" width="90" show-overflow-tooltip />
          <el-table-column label="是否有效" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.valid === false ? 'danger' : 'success'" size="small" effect="plain">
                {{ row.valid === false ? '无效' : '有效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="错误说明" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.errorMsg || '-' }}</template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button type="primary" @click="previewVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 生成数据快照 -->
    <el-dialog v-model="snapVisible" title="由数据批次生成数据快照" width="760px" top="8vh">
      <div class="dialog-body">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="快照生成后保持不变"
          description="快照固化该批次的原始数据与标准化数据，历史评估结果不会因源数据变化而被覆盖；已被评估任务使用的快照不允许删除。"
        />
        <el-descriptions :column="2" border size="small" class="snap-desc">
          <el-descriptions-item label="批次编号">
            <span class="ees-mono">{{ snapBatch?.batchCode || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="批次状态">{{ batchStatusLabel(snapBatch?.status) }}</el-descriptions-item>
          <el-descriptions-item label="有效行数">{{ fmtNum(snapBatch?.validRows, 0) }}</el-descriptions-item>
          <el-descriptions-item label="错误行数">{{ fmtNum(snapBatch?.errorRows, 0) }}</el-descriptions-item>
        </el-descriptions>

        <template v-if="!snapResult">
          <el-form label-width="150px" class="snap-form">
            <el-form-item label="指标体系版本" required>
              <el-select v-model="snapForm.indicatorVersionId" placeholder="请选择指标体系版本" filterable style="width: 320px">
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`${v.versionCode || ''} ${v.versionName || ''}`.trim()"
                  :value="v.id"
                />
              </el-select>
              <div class="ees-hint">默认取该批次导入时绑定的指标体系版本，如需对比可切换其它版本。</div>
              <!-- 同上：该批次所属项目下没有绑定任何指标体系版本时，下拉会是空的 -->
              <el-alert
                v-if="!versions.length"
                type="warning"
                :closable="false"
                show-icon
                title="该项目下没有可用的指标体系版本，无法生成快照"
                description="指标体系版本在导入时绑定项目，而该批次所属项目下没有可用版本。请到「指标体系 → 导入指标体系」为本项目导入并发布指标体系版本后重试。"
              />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="snapForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-form>
        </template>

        <template v-else>
          <el-alert type="success" :closable="false" show-icon title="数据快照生成成功" :description="`快照编号：${snapResult.snapshotCode || '-'}`" />
          <div class="ees-stat-grid report-grid">
            <StatCard label="记录数" :value="fmtNum(snapResult.recordCount, 0)" variant="primary" />
            <StatCard label="对象数" :value="fmtNum(snapResult.objectCount, 0)" />
            <StatCard label="指标数" :value="fmtNum(snapResult.indicatorCount, 0)" />
            <StatCard label="缺失数" :value="fmtNum(snapResult.missingCount, 0)" variant="warning" />
            <StatCard label="越界数" :value="fmtNum(snapResult.outlierCount, 0)" variant="warning" />
          </div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="数据时间范围">{{ snapResult.dataRange || '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-link type="primary" :underline="false" href="#/snapshots">前往数据快照查看概览 →</el-link>
        </template>
      </div>
      <template #footer>
        <el-button @click="snapVisible = false">{{ snapResult ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!snapResult" type="primary" :loading="snapSaving" @click="submitSnapshot">生成数据快照</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  ElMessage,
  ElMessageBox,
  type UploadFile,
  type UploadInstance,
  type UploadUserFile
} from 'element-plus'
import { dataBatchApi, indicatorApi, projectApi, snapshotApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  BATCH_STATUS_LABEL,
  fmtDateTime,
  fmtNum,
  parseJson
} from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/StatCard.vue'

/** ---------------- 类型 ---------------- */
type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

interface ProjectOption {
  id: number
  code?: string
  name?: string
  status?: string
}

interface VersionOption {
  id: number
  versionCode?: string
  versionName?: string
  status?: string
  indicatorCount?: number
}

interface BatchRow {
  id: number
  batchCode?: string
  projectId?: number | null
  projectCode?: string
  sourceType?: string
  sourceRef?: string
  fileName?: string
  targetVersionId?: number | null
  totalRows?: number
  validRows?: number
  errorRows?: number
  objectCount?: number
  indicatorCount?: number
  status?: string
  createdBy?: string
  createdAt?: string
}

interface ImportError {
  row?: number
  field?: string
  message?: string
  severity?: string
}

interface QualityReport {
  totalRows?: number
  validRows?: number
  errorRows?: number
  objectCount?: number
  distinctIndicatorCount?: number
  missingValueCount?: number
  outOfRangeCount?: number
  unitMismatchCount?: number
  formatErrorCount?: number
  requiredFieldMissingCount?: number
  duplicateCount?: number
  blocking?: boolean
  blockingReasons?: string[]
  ruleSeverities?: Record<string, string>
  checkedAt?: string
  batchId?: number
  batchCode?: string
  status?: string
  errors?: ImportError[]
  [key: string]: any
}

interface ImportResult {
  batchId?: number
  batchCode?: string
  totalRows?: number
  validRows?: number
  errorRows?: number
  objectCount?: number
  indicatorCount?: number
  status?: string
  qualityReport?: QualityReport
  errors?: ImportError[]
  blocking?: boolean
}

interface BatchErrorRow {
  rowNo?: number
  projectCode?: string
  objectCode?: string
  dataTime?: string
  indicatorCode?: string
  indicatorValue?: string | number | null
  unit?: string
  errorMsg?: string
}

interface RawRecord {
  rowNo?: number
  projectCode?: string
  objectCode?: string
  objectName?: string
  dataTime?: string
  indicatorCode?: string
  indicatorName?: string
  indicatorValue?: string | number | null
  unit?: string
  valid?: boolean
  errorMsg?: string
}

interface SnapshotCreateResult {
  snapshotId?: number
  snapshotCode?: string
  recordCount?: number
  objectCount?: number
  indicatorCount?: number
  missingCount?: number
  outlierCount?: number
  dataRange?: string
  qualityReport?: Record<string, any>
}

/** ---------------- 权限 ---------------- */
const userStore = useUserStore()
const canImport = computed(() => userStore.has('data:batch:import'))
const canValidate = computed(() => userStore.has('data:batch:validate'))
const canDelete = computed(() => userStore.has('data:batch:delete'))
const canSnapshot = computed(() => userStore.has('data:snapshot:create'))

/** ---------------- 字典 ---------------- */
const SOURCE_TYPE_LABEL: Record<string, string> = {
  FILE: '文件导入',
  DATABASE: '数据库抽取',
  API: '接口接入',
  SFTP: 'SFTP 同步'
}

const BATCH_STATUS_TYPE: Record<string, TagType> = {
  IMPORTED: 'info',
  VALIDATED: 'success',
  FAILED: 'danger',
  SNAPSHOTTED: 'primary'
}

const SEVERITY_META: Record<string, { label: string; type: TagType }> = {
  BLOCK: { label: '阻断', type: 'danger' },
  WARN: { label: '警告', type: 'warning' },
  INFO: { label: '提示', type: 'info' }
}

const RULE_TYPE_LABEL: Record<string, string> = {
  REQUIRED: '必填校验',
  RANGE: '范围校验',
  FORMAT: '格式校验',
  DIRECTION: '方向校验',
  DUPLICATE: '重复校验',
  MISSING: '缺失值策略',
  UNIT: '单位一致性'
}

function batchStatusLabel(status?: string): string {
  if (!status) return '-'
  return BATCH_STATUS_LABEL[status] || status
}

function batchStatusType(status?: string): TagType {
  return BATCH_STATUS_TYPE[String(status || 'IMPORTED')] || 'info'
}

function severityMeta(severity?: string): { label: string; type: TagType } {
  return SEVERITY_META[String(severity || 'WARN').toUpperCase()] || SEVERITY_META.WARN
}

function ruleTypeLabel(type?: string): string {
  if (!type) return '-'
  return RULE_TYPE_LABEL[type] || type
}

function sourceText(row: BatchRow): string {
  if (row.fileName) return row.fileName
  if (row.sourceRef) return row.sourceRef
  return '-'
}

/** ---------------- 列表 ---------------- */
const loading = ref(false)
const rows = ref<BatchRow[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const query = reactive<{ projectId: number | null; sourceType: string; status: string; keyword: string }>({
  projectId: null,
  sourceType: '',
  status: '',
  keyword: ''
})

const projects = ref<ProjectOption[]>([])
const versions = ref<VersionOption[]>([])
const downloading = ref(false)
const exporting = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await dataBatchApi.list({
      projectId: query.projectId ?? undefined,
      sourceType: query.sourceType || undefined,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
      page: page.value,
      size: size.value
    })
    rows.value = Array.isArray(res?.records) ? res.records : []
    total.value = Number(res?.total || 0)
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  query.projectId = null
  query.sourceType = ''
  query.status = ''
  query.keyword = ''
  search()
}

async function loadProjects() {
  try {
    projects.value = (await projectApi.options()) || []
  } catch {
    projects.value = []
  }
}

async function loadVersions(projectId?: number | null) {
  try {
    versions.value = (await indicatorApi.options(projectId ?? undefined)) || []
  } catch {
    versions.value = []
  }
}

async function downloadTemplate() {
  downloading.value = true
  try {
    await dataBatchApi.downloadTemplate()
    ElMessage.success('模板下载已开始')
  } catch {
    // 错误提示由统一请求层给出
  } finally {
    downloading.value = false
  }
}

async function exportErrors(batchId?: number) {
  if (batchId == null) {
    ElMessage.warning('缺少批次信息，无法导出错误明细')
    return
  }
  exporting.value = true
  try {
    await dataBatchApi.exportErrors(batchId)
    ElMessage.success('错误明细导出已开始')
  } catch {
    // 错误提示统一由请求层提示
  } finally {
    exporting.value = false
  }
}

/** ---------------- 导入 ---------------- */
const importVisible = ref(false)
const importing = ref(false)
const importStage = ref<'form' | 'result'>('form')
const importResult = ref<ImportResult | null>(null)
const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadUserFile[]>([])
const importForm = reactive<{ projectId: number | null; targetVersionId: number | null }>({
  projectId: null,
  targetVersionId: null
})

const report = computed<QualityReport>(() => importResult.value?.qualityReport || {})

async function openImport() {
  importStage.value = 'form'
  importResult.value = null
  importForm.projectId = null
  importForm.targetVersionId = null
  fileList.value = []
  importVisible.value = true
  uploadRef.value?.clearFiles()
  await loadVersions()
}

function resetImport() {
  importStage.value = 'form'
  importResult.value = null
  fileList.value = []
  uploadRef.value?.clearFiles()
}

async function onImportProjectChange(projectId?: number | null) {
  importForm.targetVersionId = null
  await loadVersions(projectId ?? null)
}

function onFileChange(file: UploadFile) {
  const name = file?.name || ''
  const dot = name.lastIndexOf('.')
  const ext = dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''
  if (!['xlsx', 'xls', 'csv'].includes(ext)) {
    ElMessage.warning('仅支持 XLSX、XLS 与 CSV 格式的数据文件')
    fileList.value = []
  }
}

function onFileExceed() {
  ElMessage.warning('只能选择一个数据文件，请先移除已选择的文件')
}

async function submitImport() {
  const file = fileList.value[0]?.raw
  if (!file) {
    ElMessage.warning('请先选择要导入的数据文件')
    return
  }
  const fd = new FormData()
  fd.append('file', file)
  if (importForm.projectId != null) fd.append('projectId', String(importForm.projectId))
  if (importForm.targetVersionId != null) fd.append('targetVersionId', String(importForm.targetVersionId))

  importing.value = true
  try {
    const res = await dataBatchApi.import(fd)
    importResult.value = res || null
    importStage.value = 'result'
    if (importResult.value?.blocking) {
      ElMessage.warning(`导入完成，存在阻断级错误，批次号：${importResult.value?.batchCode || '-'}`)
    } else {
      ElMessage.success(`导入成功，批次号：${importResult.value?.batchCode || '-'}`)
    }
    await load()
  } catch {
    // 错误提示由统一请求层给出
  } finally {
    importing.value = false
  }
}

/** ---------------- 质量报告 ---------------- */
const qualityVisible = ref(false)
const qualityLoading = ref(false)
const qualityReport = ref<QualityReport | null>(null)
const qualityErrors = ref<ImportError[]>([])
const qualityBatch = ref<BatchRow | null>(null)

const severityEntries = computed<Array<{ type: string; severity: string }>>(() => {
  const map = qualityReport.value?.ruleSeverities || {}
  return Object.keys(map).map((type) => ({ type, severity: String(map[type] || '') }))
})

async function openQuality(row: BatchRow) {
  qualityBatch.value = row
  qualityReport.value = null
  qualityErrors.value = []
  qualityVisible.value = true
  qualityLoading.value = true
  try {
    const res = await dataBatchApi.qualityReport(row.id)
    const detail = await dataBatchApi.get(row.id).catch(() => null)
    // 详情接口中的 qualityReport / errorDetail 为 JSON 字符串，统一解析后作为兜底
    const parsedQuality = parseJson<QualityReport>(detail?.qualityReport, {})
    const parsedErrors = parseJson<ImportError[]>(detail?.errorDetail, [])
    const merged: QualityReport = { ...parsedQuality, ...(res || {}) }
    qualityReport.value = merged
    const list = Array.isArray(res?.errors) && res.errors.length ? res.errors : parsedErrors
    qualityErrors.value = Array.isArray(list) ? list : []
  } catch {
    qualityReport.value = {}
    qualityErrors.value = []
  } finally {
    qualityLoading.value = false
  }
}

/** ---------------- 错误明细 ---------------- */
const errorsVisible = ref(false)
const errorsLoading = ref(false)
const errorsList = ref<BatchErrorRow[]>([])
const errorsBatch = ref<BatchRow | null>(null)

async function openErrors(row: BatchRow) {
  errorsBatch.value = row
  errorsList.value = []
  errorsVisible.value = true
  errorsLoading.value = true
  try {
    const res = await dataBatchApi.errors(row.id)
    errorsList.value = Array.isArray(res) ? res : []
  } catch {
    errorsList.value = []
  } finally {
    errorsLoading.value = false
  }
}

/** ---------------- 数据预览 ---------------- */
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewRows = ref<RawRecord[]>([])
const previewBatch = ref<BatchRow | null>(null)

async function openPreview(row: BatchRow) {
  previewBatch.value = row
  previewRows.value = []
  previewVisible.value = true
  previewLoading.value = true
  try {
    const res = await dataBatchApi.records(row.id, 200)
    previewRows.value = Array.isArray(res) ? res : []
  } catch {
    previewRows.value = []
  } finally {
    previewLoading.value = false
  }
}

/** ---------------- 行操作 ---------------- */
function onRowCommand(command: string, row: BatchRow) {
  if (command === 'revalidate') revalidate(row)
  else if (command === 'snapshot') openSnapshot(row)
  else if (command === 'delete') remove(row)
}

async function revalidate(row: BatchRow) {
  try {
    await ElMessageBox.confirm(
      `确定要按当前数据质量规则重新校验批次「${row.batchCode || row.id}」吗？重新校验会重建原始记录并刷新质量报告。`,
      '重新校验',
      { type: 'warning', confirmButtonText: '开始校验', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const res = await dataBatchApi.revalidate(row.id)
    ElMessage.success(`校验完成，批次状态：${batchStatusLabel(res?.status)}`)
    await load()
  } catch {
    // 错误提示由统一请求层给出
  }
}

async function remove(row: BatchRow) {
  try {
    await ElMessageBox.confirm(
      `确定要删除数据批次「${row.batchCode || row.id}」吗？批次下的原始记录将被清理，已生成的快照不受影响。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await dataBatchApi.remove(row.id)
    ElMessage.success('数据批次已删除')
    if (rows.value.length <= 1 && page.value > 1) page.value -= 1
    await load()
  } catch {
    // 错误提示由统一请求层给出
  }
}

/** ---------------- 生成数据快照 ---------------- */
const snapVisible = ref(false)
const snapSaving = ref(false)
const snapBatch = ref<BatchRow | null>(null)
const snapResult = ref<SnapshotCreateResult | null>(null)
const snapForm = reactive<{ indicatorVersionId: number | null; remark: string }>({
  indicatorVersionId: null,
  remark: ''
})

async function openSnapshot(row: BatchRow) {
  snapBatch.value = row
  snapResult.value = null
  snapForm.indicatorVersionId = row.targetVersionId ?? null
  snapForm.remark = ''
  snapVisible.value = true
  await loadVersions(row.projectId ?? null)
  if (snapForm.indicatorVersionId == null && versions.value.length === 1) {
    snapForm.indicatorVersionId = versions.value[0].id
  }
}

async function submitSnapshot() {
  const batch = snapBatch.value
  if (!batch) return
  if (snapForm.indicatorVersionId == null) {
    ElMessage.warning('请选择指标体系版本')
    return
  }
  snapSaving.value = true
  try {
    const res = await snapshotApi.create({
      batchId: batch.id,
      indicatorVersionId: snapForm.indicatorVersionId,
      remark: snapForm.remark.trim() || undefined
    })
    snapResult.value = res || null
    ElMessage.success(`数据快照生成成功：${snapResult.value?.snapshotCode || '-'}`)
    await load()
  } catch {
    // 错误提示由统一请求层给出
  } finally {
    snapSaving.value = false
  }
}

onMounted(async () => {
  await loadProjects()
  await load()
})
</script>

<style scoped>
.ellipsis {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-danger {
  color: var(--ees-danger);
  font-weight: 600;
}

.op-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px 6px;
}

.op-cell :deep(.el-button + .el-button) {
  margin-left: 0;
}

.dialog-body {
  max-height: 68vh;
  overflow-y: auto;
  padding-right: 4px;
}

.report-grid {
  margin-top: 14px;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.report-grid :deep(.ees-stat) {
  padding: 12px 14px;
}

.report-grid :deep(.ees-stat-value) {
  font-size: 21px;
}

.blocking-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 4px 0 10px;
  flex-wrap: wrap;
}

.blocking-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ees-text-secondary);
}

.reason-alert {
  margin-bottom: 8px;
}

.result-title {
  margin: 14px 0 8px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ees-text);
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
}

.data-upload :deep(.el-upload-list) {
  margin-top: 6px;
}

.snap-desc {
  margin: 12px 0;
}

.snap-form {
  margin-top: 12px;
}
</style>

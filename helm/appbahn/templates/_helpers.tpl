{{- define "appbahn.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "appbahn.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "appbahn.labels" -}}
helm.sh/chart: {{ include "appbahn.name" . }}-{{ .Chart.Version }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{- define "appbahn.platform.labels" -}}
{{ include "appbahn.labels" . }}
app.kubernetes.io/name: appbahn-platform
app.kubernetes.io/component: platform
{{- end }}

{{- define "appbahn.platform.selectorLabels" -}}
app.kubernetes.io/name: appbahn-platform
app.kubernetes.io/component: platform
{{- end }}

{{- define "appbahn.operator.labels" -}}
{{ include "appbahn.labels" . }}
app.kubernetes.io/name: appbahn-operator
app.kubernetes.io/component: operator
{{- end }}

{{- define "appbahn.operator.selectorLabels" -}}
app.kubernetes.io/name: appbahn-operator
app.kubernetes.io/component: operator
{{- end }}

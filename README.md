# 📊 프로젝트 리스크 분석기

Jira 프로젝트의 일정 리스크를 Monte Carlo 시뮬레이션으로 분석하는 시스템입니다.

## 🎯 주요 기능

- **Jira 연동**: Jira API를 통해 프로젝트 및 태스크 정보 자동 수집
- **Monte Carlo 시뮬레이션**: 10,000회 이상의 시뮬레이션으로 정확한 리스크 분석
- **리스크 지표**: P50, P80, P90 달성 확률 및 크리티컬 패스 분석
- **시각화 대시보드**: Streamlit 기반의 직관적인 분석 결과 표시
- **종합 의견**: AI 기반 프로젝트 상태 평가 및 권장사항 제공

## 🏗️ 시스템 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Streamlit     │    │   Spring Boot   │    │      Jira       │
│   Frontend      │◄──►│    Backend      │◄──►│      API        │
│   (Port 8501)   │    │   (Port 8080)   │    │                 │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🚀 빠른 시작

### 1. 환경 설정

```bash
# 환경 변수 파일 생성
cp env.example .env

# .env 파일 편집하여 Jira API 설정
JIRA_URL=https://your-domain.atlassian.net
JIRA_USERNAME=your-email@example.com
JIRA_PASSWORD=your-jira-password
```

### 2. Docker Compose로 실행

```bash
# 전체 시스템 실행
docker-compose up --build

# 백그라운드 실행
docker-compose up -d --build
```

### 3. 접속

- **프론트엔드**: http://localhost:8501
- **백엔드 API**: http://localhost:8080

## 📋 사용 방법

1. **프로젝트 선택**: 사이드바에서 분석할 Jira 프로젝트 선택
2. **시뮬레이션 설정**: 시뮬레이션 횟수 조정 (기본값: 10,000회)
3. **분석 실행**: "시뮬레이션 실행" 버튼 클릭
4. **결과 확인**: 대시보드에서 상세한 분석 결과 확인

## 📊 분석 결과

### 주요 지표
- **P50 달성 기간**: 50% 확률로 달성 가능한 기간
- **P80 달성 기간**: 80% 확률로 달성 가능한 기간
- **평균 기간**: 시뮬레이션 결과의 평균 소요 기간
- **표준편차**: 일정 불확실성의 정도

### 리스크 분석
- **일정 리스크**: 일정 지연 가능성
- **리소스 리스크**: 인력 할당 부족 위험
- **범위 리스크**: 요구사항 불명확성

### 시각화
- 프로젝트 기간 분포 히스토그램
- 리스크 점수 게이지 차트
- 태스크별 완료 확률 막대 그래프
- 크리티컬 패스 분석

## 🔧 기술 스택

### Backend
- **Spring Boot 3.2.0**: REST API 서버
- **Apache Commons Math**: 통계 분석 및 시뮬레이션
- **WebClient**: Jira API 연동
- **Lombok**: 코드 간소화

### Frontend
- **Streamlit 1.28.0**: 대시보드 UI
- **Plotly**: 인터랙티브 차트
- **Pandas**: 데이터 처리
- **Requests**: API 통신

### Infrastructure
- **Docker Compose**: 컨테이너 오케스트레이션
- **Java 17**: 백엔드 런타임
- **Python 3.9**: 프론트엔드 런타임

## 📁 프로젝트 구조

```
ProjectJam3/
├── docker-compose.yml          # Docker Compose 설정
├── env.example                 # 환경 변수 템플릿
├── README.md                   # 프로젝트 문서
├── backend/                    # Spring Boot 백엔드
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/projectjam/
│       ├── ProjectRiskAnalyzerApplication.java
│       ├── controller/
│       │   └── RiskAnalysisController.java
│       ├── model/
│       │   ├── JiraTask.java
│       │   └── SimulationResult.java
│       └── service/
│           ├── JiraService.java
│           └── MonteCarloService.java
└── frontend/                   # Streamlit 프론트엔드
    ├── Dockerfile
    ├── requirements.txt
    └── app.py
```

## 🔍 API 엔드포인트

### 프로젝트 관리
- `GET /api/risk-analysis/projects`: 사용 가능한 프로젝트 목록
- `GET /api/risk-analysis/projects/{projectKey}/tasks`: 프로젝트 태스크 목록

### 시뮬레이션
- `POST /api/risk-analysis/projects/{projectKey}/simulate`: Monte Carlo 시뮬레이션 실행

### 상태 확인
- `GET /api/risk-analysis/health`: 서비스 상태 확인

## 🛠️ 개발 환경 설정

### 백엔드 개발
```bash
cd backend
mvn spring-boot:run
```

### 프론트엔드 개발
```bash
cd frontend
pip install -r requirements.txt
streamlit run app.py
```

## 📈 Monte Carlo 시뮬레이션 알고리즘

1. **태스크별 확률 분포**: 삼각분포(Triangular Distribution) 사용
   - 최적치: 추정 기간의 70%
   - 최빈값: 추정 기간
   - 최악치: 추정 기간의 200%

2. **기간 추정 우선순위**:
   - **1순위**: WBSGantt 시작일/완료일 (cf10332/cf10333)
   - **2순위**: Jira 원래 추정치 (Original Estimate)
   - **3순위**: 기본값 8시간

3. **우선순위별 리스크 조정**:
   - High Priority: 불확실성 50% 증가
   - Low Priority: 불확실성 20% 감소

4. **통계 분석**:
   - 백분위수 계산 (P50, P80, P90)
   - 변동계수 기반 고위험 태스크 식별
   - 크리티컬 패스 분석

### 🔗 WBSGantt 필드 연동

시스템은 Jira의 WBSGantt 플러그인과 연동하여 더 정확한 일정 분석을 제공합니다:

- **시작일 필드**: `Start date (WBSGantt)` - 필드 ID: `cf10332`
- **완료일 필드**: `Finish date (WBSGantt)` - 필드 ID: `cf10333`

WBSGantt 필드가 설정된 태스크는 해당 기간을 우선적으로 사용하여 시뮬레이션을 수행합니다.

## 🎯 리스크 계산식

시스템은 세 가지 주요 리스크 지표를 계산하여 프로젝트의 전반적인 위험도를 평가합니다.

### 1. 일정 리스크 (Schedule Risk)

**📈 계산식:**
```
일정 리스크 = min(1.0, max(0.0, (P80 - 평균) / 평균))
```

**🔍 의미:**
- **P80 (80% 확률 달성 기간)**과 **평균 기간**의 차이를 평균으로 나눈 값
- P80이 평균보다 클수록 일정 지연 위험이 높음
- **0.0 ~ 1.0** 범위로 정규화 (최대 100%)

**📊 평가 기준:**
- **0.0 ~ 0.3 (0~30%)**: 낮음 (안전)
- **0.3 ~ 0.7 (30~70%)**: 중간 (주의)
- **0.7 ~ 1.0 (70~100%)**: 높음 (위험)

**💡 예시:**
- 평균: 100시간, P80: 120시간 → 리스크 = (120-100)/100 = **0.2 (20%)**
- 평균: 100시간, P80: 150시간 → 리스크 = (150-100)/100 = **0.5 (50%)**

### 2. 리소스 리스크 (Resource Risk)

**📈 계산식:**
```
리소스 리스크 = 할당되지 않은 태스크 수 / 전체 태스크 수
```

**🔍 의미:**
- **담당자가 없는 태스크의 비율**
- 할당되지 않은 태스크가 많을수록 리소스 부족 위험
- **0.0 ~ 1.0** 범위 (0% ~ 100%)

**📊 평가 기준:**
- **0.0 ~ 0.2 (0~20%)**: 낮음 (안전)
- **0.2 ~ 0.5 (20~50%)**: 중간 (주의)
- **0.5 ~ 1.0 (50~100%)**: 높음 (위험)

**💡 예시:**
- 전체 10개 태스크 중 2개 미할당 → 리스크 = 2/10 = **0.2 (20%)**
- 전체 10개 태스크 중 5개 미할당 → 리스크 = 5/10 = **0.5 (50%)**

### 3. 범위 리스크 (Scope Risk)

**📈 계산식:**
```
범위 리스크 = 추정치가 없는 태스크 수 / 전체 태스크 수
```

**🔍 의미:**
- **시간 추정이 없는 태스크의 비율**
- 추정치가 없으면 범위 불명확성 증가
- **0.0 ~ 1.0** 범위 (0% ~ 100%)

**📊 평가 기준:**
- **0.0 ~ 0.1 (0~10%)**: 낮음 (안전)
- **0.1 ~ 0.3 (10~30%)**: 중간 (주의)
- **0.3 ~ 1.0 (30~100%)**: 높음 (위험)

**💡 예시:**
- 전체 10개 태스크 중 1개 추정치 없음 → 리스크 = 1/10 = **0.1 (10%)**
- 전체 10개 태스크 중 3개 추정치 없음 → 리스크 = 3/10 = **0.3 (30%)**

### 🔄 종합 리스크 평가

**📈 계산식:**
```
종합 리스크 = (일정 리스크 + 리소스 리스크 + 범위 리스크) / 3
```

**📊 평가 기준:**
- **0.0 ~ 0.2 (0~20%)**: 낮음 (안전)
- **0.2 ~ 0.5 (20~50%)**: 중간 (주의)
- **0.5 ~ 1.0 (50~100%)**: 높음 (위험)

### 🎨 리스크 시각화

- **게이지 차트**: 각 리스크 유형별 점수를 직관적으로 표시
- **색상 코딩**: 
  - 🟢 녹색: 낮은 리스크 (안전)
  - 🟡 노란색: 중간 리스크 (주의)
  - 🔴 빨간색: 높은 리스크 (위험)
- **실시간 업데이트**: 시뮬레이션 결과에 따라 동적 업데이트

## 🔒 보안 고려사항

- Jira API 토큰은 환경 변수로 관리
- HTTPS 통신 권장
- API 요청 제한 및 타임아웃 설정

## 🤝 기여하기

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 🆘 문제 해결

### 일반적인 문제

1. **Jira 연결 실패**
   - 사용자명과 패스워드가 올바른지 확인
   - Jira URL이 정확한지 확인
   - 네트워크 연결 상태 확인

2. **시뮬레이션 시간 초과**
   - 시뮬레이션 횟수를 줄여보세요
   - 서버 리소스 확인

3. **Docker 실행 오류**
   - Docker 및 Docker Compose 설치 확인
   - 포트 충돌 확인 (8080, 8501)

### 로그 확인

```bash
# 전체 로그 확인
docker-compose logs

# 특정 서비스 로그 확인
docker-compose logs backend
docker-compose logs frontend
``` 
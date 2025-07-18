import streamlit as st
import requests
import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import numpy as np
from datetime import datetime
import json

# 페이지 설정
st.set_page_config(
    page_title="프로젝트 리스크 분석기",
    page_icon="📊",
    layout="wide",
    initial_sidebar_state="expanded"
)

# 환경 변수
import os
BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080")

def main():
    st.title("📊 프로젝트 리스크 분석기")
    st.markdown("---")
    
    # 사이드바 설정
    with st.sidebar:
        st.header("⚙️ 설정")
        
        # 프로젝트 검색
        st.subheader("프로젝트 검색")
        
        # 검색어 입력
        search_query = st.text_input(
            "프로젝트명 또는 프로젝트키를 입력하세요:",
            placeholder="예: PROJECT, 프로젝트명",
            help="프로젝트 키(예: PROJECT) 또는 프로젝트명의 일부를 입력하면 검색됩니다."
        )
        
        selected_project = None
        
        if search_query and len(search_query.strip()) >= 2:
            try:
                search_response = requests.get(
                    f"{BACKEND_URL}/api/risk-analysis/projects/search",
                    params={"query": search_query.strip()},
                    timeout=10
                )
                
                if search_response.status_code == 200:
                    projects = search_response.json()
                    
                    if projects:
                        # 프로젝트 선택 옵션 생성
                        project_options = [f"{p['key']} - {p['name']}" for p in projects]
                        project_keys = [p['key'] for p in projects]
                        
                        selected_option = st.selectbox(
                            "검색 결과에서 프로젝트를 선택하세요:",
                            project_options,
                            index=0
                        )
                        
                        if selected_option:
                            selected_project = selected_option.split(" - ")[0]
                    else:
                        st.info("검색 결과가 없습니다. 다른 검색어를 시도해보세요.")
                else:
                    st.error("프로젝트 검색 중 오류가 발생했습니다.")
                    
            except Exception as e:
                st.error(f"백엔드 연결 오류: {str(e)}")
        elif search_query and len(search_query.strip()) < 2:
            st.info("검색어를 2글자 이상 입력해주세요.")
        else:
            st.info("프로젝트를 검색하려면 위에 검색어를 입력하세요.")
        
        # 시뮬레이션 설정
        st.subheader("시뮬레이션 설정")
        num_simulations = st.slider(
            "시뮬레이션 횟수:",
            min_value=1000,
            max_value=50000,
            value=10000,
            step=1000,
            help="더 많은 시뮬레이션은 정확한 결과를 제공하지만 시간이 더 오래 걸립니다."
        )
        
        # 시뮬레이션 실행 버튼
        if selected_project:
            if st.button("🚀 시뮬레이션 실행", type="primary"):
                run_simulation(selected_project, num_simulations)
    
    # 메인 콘텐츠
    if 'simulation_result' in st.session_state:
        display_results(st.session_state.simulation_result)
    else:
        st.info("👈 사이드바에서 프로젝트를 선택하고 시뮬레이션을 실행하세요.")

def run_simulation(project_key, num_simulations):
    """Monte Carlo 시뮬레이션을 실행합니다."""
    with st.spinner(f"🔄 {project_key} 프로젝트에 대한 Monte Carlo 시뮬레이션을 실행 중..."):
        try:
            response = requests.post(
                f"{BACKEND_URL}/api/risk-analysis/projects/{project_key}/simulate",
                json={"numSimulations": num_simulations},
                timeout=300  # 5분 타임아웃
            )
            
            if response.status_code == 200:
                result = response.json()
                st.session_state.simulation_result = result
                st.success(f"✅ {project_key} 프로젝트 시뮬레이션 완료!")
                st.rerun()
            else:
                st.error(f"시뮬레이션 실행 실패: {response.status_code}")
                
        except Exception as e:
            st.error(f"시뮬레이션 실행 중 오류 발생: {str(e)}")

def display_results(result):
    """시뮬레이션 결과를 표시합니다."""
    st.header(f"📈 {result['projectKey']} 프로젝트 분석 결과")
    
    # 종합 의견
    st.subheader("🎯 종합 의견")
    st.info(result['overallAssessment'])
    
    # 주요 지표
    col1, col2, col3, col4 = st.columns(4)
    
    with col1:
        st.metric(
            "P50 달성 기간",
            f"{result['p50Duration']:.1f}시간",
            f"{result['p50Duration']/8:.1f}일"
        )
    
    with col2:
        st.metric(
            "P80 달성 기간",
            f"{result['p80Duration']:.1f}시간",
            f"{result['p80Duration']/8:.1f}일"
        )
    
    with col3:
        st.metric(
            "평균 기간",
            f"{result['meanDuration']:.1f}시간",
            f"{result['meanDuration']/8:.1f}일"
        )
    
    with col4:
        st.metric(
            "표준편차",
            f"{result['standardDeviation']:.1f}시간",
            f"{result['standardDeviation']/8:.1f}일"
        )
    
    # 차트 섹션
    col1, col2 = st.columns(2)
    
    with col1:
        display_duration_distribution(result)
    
    with col2:
        display_risk_analysis(result)
    
    # 크리티컬 패스
    st.subheader("🔗 크리티컬 패스")
    if result['criticalPath']:
        # 백엔드에서 태스크 정보를 가져와서 표시
        try:
            tasks_response = requests.get(
                f"{BACKEND_URL}/api/risk-analysis/projects/{result['projectKey']}/tasks/lightweight",
                timeout=15  # lightweight API는 더 빠르므로 타임아웃 줄임
            )
            
            if tasks_response.status_code == 200:
                tasks_data = tasks_response.json()
                task_info = {task['key']: task['summary'] for task in tasks_data}
                
                # Jira URL 설정
                jira_base_url = os.environ.get("JIRA_URL", "http://localhost:8080")
                
                # 크리티컬 패스 테이블 생성
                critical_path_data = []
                for i, task_key in enumerate(result['criticalPath'], 1):
                    task_name = task_info.get(task_key, "이름 없음")
                    jira_link = f"{jira_base_url}/browse/{task_key}"
                    
                    # 태스크별 분석 정보 가져오기
                    task_analysis = get_task_analysis(task_key, result)
                    
                    critical_path_data.append({
                        '순위': i,
                        'Task Key': f"[{task_key}]({jira_link})",
                        'Task Name': task_name,
                        '완료 확률': task_analysis['completion_probability'],
                        '예상 소요시간': task_analysis['estimated_duration'],
                        '낙관적/비관적': f"{task_analysis['optimistic_duration']} / {task_analysis['pessimistic_duration']}",
                        '리스크 레벨': task_analysis['risk_level'],
                        '담당자': task_analysis['assignee'] or "미할당",
                        '우선순위': task_analysis['priority'] or "보통"
                    })
                
                # 데이터프레임으로 표시
                critical_path_df = pd.DataFrame(critical_path_data)
                
                # CSS 스타일 적용
                st.markdown("""
                <style>
                .critical-path-table {
                    font-size: 14px;
                    line-height: 1.2;
                }
                .critical-path-table th {
                    background-color: #f0f2f6;
                    padding: 8px 12px;
                    border-bottom: 2px solid #ddd;
                }
                .critical-path-table td {
                    padding: 6px 12px;
                    border-bottom: 1px solid #eee;
                }
                </style>
                """, unsafe_allow_html=True)
                
                # 테이블 표시
                st.markdown('<div class="critical-path-table">', unsafe_allow_html=True)
                st.dataframe(
                    critical_path_df,
                    use_container_width=True,
                    hide_index=True,
                    column_config={
                        "순위": st.column_config.NumberColumn("순위", width="small"),
                        "Task Key": st.column_config.LinkColumn("Task Key", width="medium"),
                        "Task Name": st.column_config.TextColumn("Task Name", width="large"),
                        "완료 확률": st.column_config.ProgressColumn("완료 확률", min_value=0, max_value=1, width="small"),
                        "예상 소요시간": st.column_config.TextColumn("예상 소요시간", width="small"),
                        "낙관적/비관적": st.column_config.TextColumn("낙관적/비관적", width="medium"),
                        "리스크 레벨": st.column_config.SelectboxColumn("리스크 레벨", width="small"),
                        "담당자": st.column_config.TextColumn("담당자", width="small"),
                        "우선순위": st.column_config.TextColumn("우선순위", width="small")
                    }
                )
                st.markdown('</div>', unsafe_allow_html=True)
                
                # 크리티컬 패스 분석 요약
                st.markdown("**📊 크리티컬 패스 분석 요약**")
                total_tasks = len(result['criticalPath'])
                high_risk_tasks = sum(1 for task in critical_path_data if task['리스크 레벨'] == '높음')
                avg_completion_prob = sum(task['완료 확률'] for task in critical_path_data) / total_tasks
                
                col1, col2, col3 = st.columns(3)
                with col1:
                    st.metric("총 태스크 수", total_tasks)
                with col2:
                    st.metric("고위험 태스크", high_risk_tasks, f"{high_risk_tasks/total_tasks*100:.1f}%")
                with col3:
                    st.metric("평균 완료 확률", f"{avg_completion_prob*100:.1f}%")
                    
            else:
                # 태스크 정보를 가져올 수 없는 경우 기본 표시
                display_simple_critical_path(result['criticalPath'])
                    
        except Exception as e:
            st.warning(f"태스크 정보를 가져오는 중 오류가 발생했습니다: {str(e)}")
            display_simple_critical_path(result['criticalPath'])
    else:
        st.info("크리티컬 패스 정보가 없습니다.")
    
    # 태스크별 완료 확률
    st.subheader("📊 태스크별 완료 확률")
    if result['taskCompletionProbabilities']:
        task_probs_df = pd.DataFrame([
            {'Task Key': k, '완료 확률': f"{v*100:.1f}%"}
            for k, v in result['taskCompletionProbabilities'].items()
        ]).sort_values('완료 확률', ascending=False)
        
        fig = px.bar(
            task_probs_df.head(10),
            x='Task Key',
            y='완료 확률',
            color='완료 확률',
            color_continuous_scale='RdYlGn'
        )
        fig.update_layout(
            title="상위 10개 태스크 완료 확률",
            xaxis_title="Task Key",
            yaxis_title="완료 확률 (%)",
            xaxis_tickangle=-45,
            height=400
        )
        st.plotly_chart(fig, use_container_width=True)
        
        st.markdown("""
        **📊 차트 설명:**
        - 이 차트는 각 태스크의 완료 확률을 보여줍니다
        - 높은 확률(녹색)은 태스크가 예상대로 완료될 가능성이 높음을 의미합니다
        - 낮은 확률(빨간색)은 태스크에 지연 위험이 있음을 나타냅니다
        """)
    
    # 권장사항
    st.subheader("💡 권장사항")
    if result['riskAnalysis']['recommendations']:
        for i, rec in enumerate(result['riskAnalysis']['recommendations'], 1):
            st.write(f"{i}. {rec}")
    else:
        st.info("특별한 권장사항이 없습니다.")

def display_duration_distribution(result):
    """프로젝트 기간 분포를 표시합니다."""
    st.subheader("📊 프로젝트 기간 분포")
    
    # 히스토그램
    fig = go.Figure()
    
    fig.add_trace(go.Histogram(
        x=result['durationDistribution'],
        nbinsx=50,
        name="시뮬레이션 결과",
        marker_color='skyblue',
        opacity=0.7
    ))
    
    # P50, P80, P90 라인 추가
    fig.add_vline(x=result['p50Duration'], line_dash="dash", line_color="green", 
                  annotation_text=f"P50: {result['p50Duration']:.1f}h")
    fig.add_vline(x=result['p80Duration'], line_dash="dash", line_color="orange", 
                  annotation_text=f"P80: {result['p80Duration']:.1f}h")
    fig.add_vline(x=result['p90Duration'], line_dash="dash", line_color="red", 
                  annotation_text=f"P90: {result['p90Duration']:.1f}h")
    
    fig.update_layout(
        title="프로젝트 완료 기간 분포",
        xaxis_title="시간 (시간)",
        yaxis_title="빈도",
        showlegend=False,
        height=400,
        margin=dict(l=50, r=50, t=80, b=50)
    )
    
    st.plotly_chart(fig, use_container_width=True)
    
    st.markdown("""
    **📊 차트 설명:**
    - 이 히스토그램은 Monte Carlo 시뮬레이션 결과를 보여줍니다
    - **P50 (녹색선)**: 50% 확률로 달성 가능한 기간
    - **P80 (주황선)**: 80% 확률로 달성 가능한 기간  
    - **P90 (빨간선)**: 90% 확률로 달성 가능한 기간
    - 분포가 오른쪽으로 치우칠수록 지연 위험이 높습니다
    """)

def display_risk_analysis(result):
    """리스크 분석 결과를 표시합니다."""
    st.subheader("⚠️ 리스크 분석")
    
    risk_data = result['riskAnalysis']
    
    # 리스크 점수 게이지 차트
    fig = make_subplots(
        rows=1, cols=3,
        specs=[[{"type": "indicator"}, {"type": "indicator"}, {"type": "indicator"}]],
        subplot_titles=("일정 리스크", "리소스 리스크", "범위 리스크")
    )
    
    # 일정 리스크
    fig.add_trace(go.Indicator(
        mode="gauge+number+delta",
        value=risk_data['scheduleRisk'] * 100,
        domain={'x': [0, 1], 'y': [0, 1]},
        title={'text': "일정 리스크 (%)"},
        gauge={
            'axis': {'range': [None, 100]},
            'bar': {'color': "darkblue"},
            'steps': [
                {'range': [0, 30], 'color': "lightgreen"},
                {'range': [30, 70], 'color': "yellow"},
                {'range': [70, 100], 'color': "red"}
            ],
            'threshold': {
                'line': {'color': "red", 'width': 4},
                'thickness': 0.75,
                'value': 70
            }
        }
    ), row=1, col=1)
    
    # 리소스 리스크
    fig.add_trace(go.Indicator(
        mode="gauge+number+delta",
        value=risk_data['resourceRisk'] * 100,
        domain={'x': [0, 1], 'y': [0, 1]},
        title={'text': "리소스 리스크 (%)"},
        gauge={
            'axis': {'range': [None, 100]},
            'bar': {'color': "darkblue"},
            'steps': [
                {'range': [0, 20], 'color': "lightgreen"},
                {'range': [20, 50], 'color': "yellow"},
                {'range': [50, 100], 'color': "red"}
            ],
            'threshold': {
                'line': {'color': "red", 'width': 4},
                'thickness': 0.75,
                'value': 50
            }
        }
    ), row=1, col=2)
    
    # 범위 리스크
    fig.add_trace(go.Indicator(
        mode="gauge+number+delta",
        value=risk_data['scopeRisk'] * 100,
        domain={'x': [0, 1], 'y': [0, 1]},
        title={'text': "범위 리스크 (%)"},
        gauge={
            'axis': {'range': [None, 100]},
            'bar': {'color': "darkblue"},
            'steps': [
                {'range': [0, 10], 'color': "lightgreen"},
                {'range': [10, 30], 'color': "yellow"},
                {'range': [30, 100], 'color': "red"}
            ],
            'threshold': {
                'line': {'color': "red", 'width': 4},
                'thickness': 0.75,
                'value': 30
            }
        }
    ), row=1, col=3)
    
    fig.update_layout(
        height=350,
        margin=dict(l=20, r=20, t=80, b=20)
    )
    st.plotly_chart(fig, use_container_width=True)
    
    # 고위험 태스크
    if risk_data['highRiskTasks']:
        st.warning(f"🚨 고위험 태스크: {', '.join(risk_data['highRiskTasks'])}")
    
    st.markdown("""
    **⚠️ 리스크 분석 설명:**
    - **일정 리스크**: 프로젝트 일정 지연 가능성을 나타냅니다 (30% 이하: 안전, 30-70%: 주의, 70% 이상: 위험)
    - **리소스 리스크**: 인력, 예산 등 리소스 부족 가능성을 나타냅니다 (20% 이하: 안전, 20-50%: 주의, 50% 이상: 위험)
    - **범위 리스크**: 요구사항 변경이나 범위 확장 가능성을 나타냅니다 (10% 이하: 안전, 10-30%: 주의, 30% 이상: 위험)
    """)

def get_task_analysis(task_key, result):
    """태스크별 분석 정보를 반환합니다."""
    # 백엔드에서 제공하는 상세 분석 정보 사용
    if 'taskAnalyses' in result and task_key in result['taskAnalyses']:
        task_analysis = result['taskAnalyses'][task_key]
        return {
            'completion_probability': task_analysis['completionProbability'],
            'estimated_duration': f"{task_analysis['estimatedDuration']:.1f}시간",
            'risk_level': task_analysis['riskLevel'],
            'optimistic_duration': f"{task_analysis['optimisticDuration']:.1f}시간",
            'pessimistic_duration': f"{task_analysis['pessimisticDuration']:.1f}시간",
            'variability': task_analysis['variability'],
            'status': task_analysis['status'],
            'assignee': task_analysis['assignee'],
            'priority': task_analysis['priority']
        }
    else:
        # 기본값 (백엔드에서 정보를 제공하지 않는 경우)
        completion_probability = result['taskCompletionProbabilities'].get(task_key, 0.5)
        
        if completion_probability >= 0.8:
            risk_level = "낮음"
        elif completion_probability >= 0.6:
            risk_level = "보통"
        else:
            risk_level = "높음"
        
        return {
            'completion_probability': completion_probability,
            'estimated_duration': "8-16시간",
            'risk_level': risk_level,
            'optimistic_duration': "6-12시간",
            'pessimistic_duration': "16-24시간",
            'variability': 0.3,
            'status': "진행중",
            'assignee': "미할당",
            'priority': "보통"
        }

def display_simple_critical_path(critical_path):
    """간단한 크리티컬 패스 표시"""
    st.markdown("**순위 | Task Key**")
    st.markdown("---")
    jira_base_url = os.environ.get("JIRA_URL", "http://localhost:8080")
    
    for i, task_key in enumerate(critical_path, 1):
        jira_link = f"{jira_base_url}/browse/{task_key}"
        st.markdown(f"**{i}** | [{task_key}]({jira_link})")

if __name__ == "__main__":
    main() 
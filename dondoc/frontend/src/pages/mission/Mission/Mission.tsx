import styles from "./Mission.module.css";
import { useState, useEffect } from 'react'
import Header from "../../webmain/Header/Header";
import Nav from "../../Nav/Nav";
import { useSelector } from "react-redux/es/hooks/useSelector";
import { UserType } from "../../../store/slice/userSlice";
import axios from "axios";
import { BASE_URL } from "../../../constants";
import { useNavigate } from "react-router-dom";
import Mission_Detail from "./Detail/Mission_Detail";


type Missions = {
  id: number,
  moimName: string,
  title: string,
  content: string,
  amount: number,
  endDate: string
}



function Mission() {

  const [MissionList, setMissionList] = useState<Missions[]>([])
  const [OpenModal, setOpenModal] = useState<boolean>(false)

  const userInfo:UserType = useSelector((state:{user:UserType})=>{
    return state.user
  })

  const navigate = useNavigate()
  const token = userInfo.accessToken

  const ModalOpen = () => {
    setOpenModal(true)
  }

  useEffect(() => {
    axios.get(`${BASE_URL}/api/moim/my_mission`, {
      headers:{
        Authorization: `Bearer ${token}`}
    })
    .then((res) => {
      console.log(res.data)
      setMissionList(res.data.response)
      if (res.data.success === false) {
        navigate('/signin')
      }
    })
    .catch((err) => {
      console.log(err)
    })
  },[])

  return (
    <>
    <Header />
    <div>
    <img style={{marginTop:"5%",marginBottom:"2%",marginLeft:"30%",width:"40%"}} src={`/src/assets/characterImg/${userInfo.imageNumber}.png`} alt="" />
    <div style={{marginTop:"5%",marginBottom:"2%",marginLeft:"33%",width:"40%", fontSize:"2.8rem"}}>나의 미션</div>
    </div>
    <div className={styles.List}>

    { MissionList.length ?
    (MissionList.map((mi) => (
      <div className={styles.topContainer} onClick={ModalOpen}>
      <div style={{display:"flex",width:"100%"}}>
      <img src={`/src/assets/MoimLogo/dondoclogo.svg`} style={{width:"25%", marginLeft:"1rem"}} />
    <div style={{marginLeft:"5rem",textAlign:"left", width:"30%"}}>
      <p style={{fontSize:"1.5rem",fontWeight:"bold", color:"skyblue", margin:"0"}}>{mi.moimName}</p>
      <p style={{fontSize:"1.2rem"}}>{mi.title}</p>
      <p style={{fontSize:"1.2rem", marginTop:"0"}}>{mi.endDate}까지</p>
    </div>
    <div className={styles.amount}>
      <p style={{fontSize:"1.5rem",fontWeight:"bold", marginBottom:"0"}}>{mi.amount}원</p>
    </div>
      </div> 
      </div> 
))) :
    <div className={styles.ResultContainer}>등록된 미션이 없습니다.</div>}


    </div>
      <Mission_Detail />
      <Nav />
    </>
  );
}

export default Mission;



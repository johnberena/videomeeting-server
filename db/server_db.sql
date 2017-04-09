drop database if exists streaming;
create database streaming;

use streaming;

create table users_stats (
	id integer not null auto_increment,
	login_name varchar(20) not null default '',
	virtual_room varchar(20) not null default '0',
	ip varchar(256) null,
	referrer varchar(256) null,
	flash_version varchar(256) null,
	login_timestamp double,
	primary key(id)
);

create table course_status (
	id integer not null auto_increment,
	virtual_room varchar(20) not null default '0',
	current_page tinyint not null default '0',
	old_page tinyint not null default '0',
	record_timestamp double not null default '0',
	last_change double,
	status tinyint not null default '0', -- 0: not use, 1: start, 2: stop
	primary key(id)
);

create table stream_files (
	id integer not null auto_increment,
	login_name varchar(20) not null default '',
	virtual_room varchar(20) not null default '0',
	filename varchar(256) not null default '',
	type tinyint not null default '0',  -- 1: full stream, 2: sub stream
	content_page tinyint not null default '0',
	create_timestamp double,
	duration int default '0',
	status tinyint not null default '0',  -- 0: not use, 1: recording, 2: finish
	primary key(id)
);

